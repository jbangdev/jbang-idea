package dev.jbang.idea.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER
import com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.util.ProcessingContext
import dev.jbang.idea.mavenIcon
import org.jetbrains.concurrency.Promise.State.PENDING
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import org.jetbrains.idea.reposearch.SearchParameters
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Consumer


class JBangDepsGavCompletionContributor : JBangBaseDirectiveCompletionContributor() {
    init {
        extend(
            CompletionType.BASIC, PlatformPatterns.psiElement(PsiComment::class.java).withLanguage(JavaLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val comment = parameters.position as PsiComment
                    if (comment.text.startsWith("//DEPS ")) {
                        val searchText = trimDummy(comment.text.substring(7).trim())
                        val startOffset = comment.textRange.startOffset + 7
                        val searchService = DependencySearchService.getInstance(comment.project)
                        val searchParameters = SearchParameters(parameters.invocationCount < 2, false)
                        val cld = ConcurrentLinkedDeque<RepositoryArtifactData>()
                        val parts = searchText.split(':')
                        val searchVersion = parts.size == 3
                        val promise = if (parts.size < 2) {
                            searchService.fulltextSearch(searchText, searchParameters, Consumer { cld.add(it) })
                        } else {
                            searchService.suggestPrefix(parts[0], parts[1], searchParameters, Consumer { cld.add(it) })
                        }
                        while (promise.state === PENDING || !cld.isEmpty()) {
                            ProgressManager.checkCanceled()
                            val item = cld.poll()
                            if (item != null && item is MavenRepositoryArtifactInfo) {
                                val lookupString = if (searchVersion) {
                                    item.version.toString()
                                } else {
                                    item.key
                                }
                                result.addElement(
                                    LookupElementBuilder.create(item, lookupString).withIcon(mavenIcon)
                                        .withInsertHandler { insertionContext, selectedItem ->
                                            var selectedText = selectedItem.lookupString
                                            if (searchVersion) {
                                                val artifactInfo = selectedItem.`object` as MavenRepositoryArtifactInfo
                                                selectedText = artifactInfo.key + ":" + selectedText
                                            }
                                            // replace GAV because some special characters(`[:-]`) that make code completion failed
                                            val editor = insertionContext.editor
                                            val document = insertionContext.document
                                            val currentOffset = editor.caretModel.offset
                                            document.deleteString(startOffset, currentOffset)
                                            document.insertString(startOffset, selectedText);
                                            editor.caretModel.moveToOffset(startOffset + selectedText.length)
                                        }
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    private fun trimDummy(value: String): String {
        return StringUtil.trim(value.replace(DUMMY_IDENTIFIER, "").replace(DUMMY_IDENTIFIER_TRIMMED, ""))
    }
}
