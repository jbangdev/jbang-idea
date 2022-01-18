package dev.jbang.idea.completion.directive

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.openapi.project.DumbAware


open class JBangBaseDirectiveCompletionContributor : CompletionContributor(), DumbAware {
    companion object {
        val JAVA_DIRECTIVES = listOf("JAVA ", "DEPS ", "GAV ", "FILES ", "SOURCES ", "DESCRIPTION ", "REPOS ", "JAVAC_OPTIONS ", "JAVA_OPTIONS ", "JAVAAGENT ", "CDS")
    }

    fun shouldCompleteForDirective(commentText: String): Boolean {
        return commentText.startsWith("//") && !commentText.trim().contains(' ')
    }
}