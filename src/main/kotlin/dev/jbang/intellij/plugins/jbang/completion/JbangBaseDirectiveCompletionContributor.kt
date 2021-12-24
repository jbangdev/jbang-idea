package dev.jbang.intellij.plugins.jbang.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.openapi.project.DumbAware


open class JbangBaseDirectiveCompletionContributor : CompletionContributor(), DumbAware {
    companion object {
        val JAVA_DIRECTIVES = listOf("JAVA", "DEPS", "GAV", "FILES", "SOURCES", "DESCRIPTION", "REPOS", "JAVAC_OPTIONS", "JAVA_OPTIONS", "JAVAAGENT", "CDS")
    }
}