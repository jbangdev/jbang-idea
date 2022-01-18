package dev.jbang.idea.completion.deps

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.java.JavaLanguage
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment


class JBangJavaDepsGavCompletionContributor : JBangDepsGavBaseCompletionContributor() {
    init {
        extend(
            CompletionType.BASIC, PlatformPatterns.psiElement(PsiComment::class.java).withLanguage(JavaLanguage.INSTANCE), completionProvider()
        )
    }

}
