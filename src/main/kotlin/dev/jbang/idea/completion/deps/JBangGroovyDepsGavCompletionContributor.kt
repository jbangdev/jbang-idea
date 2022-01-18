package dev.jbang.idea.completion.deps

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import org.jetbrains.plugins.groovy.GroovyLanguage

class JBangGroovyDepsGavCompletionContributor : JBangDepsGavBaseCompletionContributor() {
    init {
        extend(
            CompletionType.BASIC, PlatformPatterns.psiElement(PsiComment::class.java).withLanguage(GroovyLanguage), completionProvider()
        )
    }

}
