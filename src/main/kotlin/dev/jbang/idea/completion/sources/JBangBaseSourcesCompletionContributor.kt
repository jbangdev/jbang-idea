package dev.jbang.idea.completion.sources

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import javax.swing.Icon


abstract class JBangBaseSourcesCompletionContributor(language:Language) : CompletionContributor(), DumbAware {

    fun shouldCompleteForDirective(commentText: String): Boolean {
        return commentText.startsWith("//SOURCES ")
    }

    open fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
        comment: PsiComment
    ) {
        val commentText = comment.text
        val sourceFile = StringUtil.trim(
            commentText.substring(commentText.indexOf(' ') + 1)
                .replace(CompletionUtil.DUMMY_IDENTIFIER, "")
                .replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, "")
        )
        val parts = sourceFile.split("[/\\\\]".toRegex())
        val currentFile = comment.containingFile.originalFile
        var destDir = currentFile.parent
        if (parts.size > 1) {
            for (item in parts.subList(0, parts.size - 1)) {
                if (item == "..") {
                    destDir = destDir?.parentDirectory
                } else if (item != ".") {
                    destDir = destDir?.findSubdirectory(item)
                }
            }
        }
        if (destDir != null) {
            val extName = allowedExtName()
            destDir.children
                .filter {
                    it is PsiDirectory || (it is PsiFile
                            && it.name.endsWith(extName)
                            && it.name != currentFile.name)
                }
                .forEach {
                    val item = it as PsiNamedElement
                    var name = item.name
                    val icon = if (item is PsiDirectory) {
                        name = "$name/"
                        AllIcons.Actions.ProjectDirectory
                    } else {
                        fileTypeIcon()
                    }
                    result.addElement(LookupElementBuilder.create(name!!).withIcon(icon))
                }
        }
    }

    init {
        extend(
            CompletionType.BASIC, PlatformPatterns.psiElement(PsiComment::class.java).withLanguage(language),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val comment = parameters.position as PsiComment
                    val commentParent = comment.parent
                    if (commentParent is PsiClass || commentParent is PsiFile) {
                        if (shouldCompleteForDirective(comment.text)) {
                            addCompletions(parameters, context, result, comment)
                        }
                    }
                }
            }
        )
    }

    protected abstract fun allowedExtName(): String
    protected abstract fun fileTypeIcon(): Icon

}