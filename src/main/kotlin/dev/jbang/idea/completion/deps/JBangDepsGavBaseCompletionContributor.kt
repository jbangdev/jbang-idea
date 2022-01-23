package dev.jbang.idea.completion.deps

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
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

@Suppress("UnstableApiUsage")
open class JBangDepsGavBaseCompletionContributor : CompletionContributor(), DumbAware {

    protected fun completionProvider() = object : CompletionProvider<CompletionParameters>() {
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
                            LookupElementBuilder.create(searchText, lookupString).withIcon(mavenIcon)
                                .withInsertHandler { insertionContext, selectedItem ->
                                    var selectedText = selectedItem.lookupString
                                    if (searchVersion) {
                                        val artifactInfo = searchText.substring(0, searchText.lastIndexOf(':'))
                                        selectedText = "$artifactInfo:$selectedText"
                                    }
                                    // replace GAV because some special characters(`[:-_]`) that make code completion not inserted correctly
                                    val editor = insertionContext.editor
                                    val document = insertionContext.document
                                    val currentOffset = editor.caretModel.offset
                                    document.deleteString(startOffset, currentOffset)
                                    document.insertString(startOffset, selectedText)
                                    editor.caretModel.moveToOffset(startOffset + selectedText.length)
                                }
                        )
                    }
                }
            }
        }
    }

    protected fun trimDummy(value: String): String {
        return StringUtil.trim(value.replace(CompletionUtil.DUMMY_IDENTIFIER, "").replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, ""))
    }
}