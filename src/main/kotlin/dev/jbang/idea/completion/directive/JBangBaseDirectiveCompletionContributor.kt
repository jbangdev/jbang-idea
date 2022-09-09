package dev.jbang.idea.completion.directive

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.Language
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext


abstract class JBangBaseDirectiveCompletionContributor(language: Language) : CompletionContributor(), DumbAware {
    companion object {
        val JAVA_DIRECTIVES = mapOf(
            "JAVA" to "Java version to use",
            "DEPS" to "Add dependency",
            "GAV" to "Set Group, Artifact and Version",
            "MANIFEST" to "Write entries to META-INF/manifest.mf",
            "FILES" to "Mount files to build",
            "SOURCES" to "Pattern to include as sources",
            "DESCRIPTION" to "Markdown description for the application/script",
            "REPOS" to "Which repositories to use",
            "JAVAC_OPTIONS" to "Options passed to javac",
            "JAVA_OPTIONS" to "Options passed to java",
            "JAVAAGENT" to "Activate agent packaging",
            "CDS" to "Activate Class Data Sharing"
        )
    }

    fun shouldCompleteForDirective(commentText: String): Boolean {

        return commentText.startsWith("//") && !commentText.trim().contains(' ')
    }

    open fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        JAVA_DIRECTIVES.forEach {
            result.addElement(LookupElementBuilder.create(it.key + " ").appendTailText(" " + it.value, true).withPresentableText(it.key))
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
                            this@JBangBaseDirectiveCompletionContributor.addCompletions(parameters, context, result)
                        }
                    }
                }
            }
        )
    }
}