package dev.jbang.idea.completion.deps

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.repository.search.completion.api.DependencyCompletionContextImpl
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.util.ProcessingContext
import dev.jbang.idea.mavenIcon

@Suppress("UnstableApiUsage")
open class JBangDepsGavBaseCompletionContributor : CompletionContributor(), DumbAware {

    protected fun completionProvider() = object : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val comment = parameters.position as PsiComment
            if (!comment.text.startsWith("//DEPS ")) return
            val searchText = trimDummy(comment.text.substring(7).trim())
            val startOffset = comment.textRange.startOffset + 7
            val parts = searchText.split(':')
            val searchVersion = parts.size == 3

            val searchService = service<DependencyCompletionService>()
            val searchContext = DependencyCompletionContextImpl(comment.project, ProjectSystemId("GRADLE"))

            fun addLookup(lookupString: String) {
                result.addElement(
                    LookupElementBuilder.create(searchText, lookupString).withIcon(mavenIcon)
                        .withInsertHandler { insertionContext, selectedItem ->
                            var selectedText = selectedItem.lookupString
                            if (searchVersion) {
                                val artifactInfo = searchText.substring(0, searchText.lastIndexOf(':'))
                                selectedText = "$artifactInfo:$selectedText"
                            }
                            // replace GAV because some special characters(`[:-_]`) make code completion not inserted correctly
                            val editor = insertionContext.editor
                            val document = insertionContext.document
                            val currentOffset = editor.caretModel.offset
                            document.deleteString(startOffset, currentOffset)
                            document.insertString(startOffset, selectedText)
                            editor.caretModel.moveToOffset(startOffset + selectedText.length)
                        }
                )
            }

            runBlockingCancellable {
                if (searchVersion) {
                    val request = DependencyVersionCompletionRequest(parts[0], parts[1], parts[2], searchContext)
                    searchService.suggestVersionCompletions(request).collect { event ->
                        if (event is DependencyCompletionEvent.Item) addLookup(event.result.result)
                    }
                } else {
                    val request = DependencyCompletionRequest(searchText, searchContext)
                    searchService.suggestCompletions(request).collect { event ->
                        if (event is DependencyCompletionEvent.Item) {
                            val item = event.result
                            addLookup("${item.groupId}:${item.artifactId}:${item.version}")
                        }
                    }
                }
            }
        }
    }

    protected fun trimDummy(value: String): String {
        return StringUtil.trim(value.replace(CompletionUtil.DUMMY_IDENTIFIER, "").replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, ""))
    }
}
