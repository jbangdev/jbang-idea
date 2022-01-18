package dev.jbang.idea.completion.deps

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.idea.KotlinLanguage


class JBangKotlinDepsGavCompletionContributor : JBangDepsGavBaseCompletionContributor() {
    init {
        extend(
            CompletionType.BASIC, PlatformPatterns.psiElement(PsiComment::class.java).withLanguage(KotlinLanguage.INSTANCE), completionProvider()
        )
    }

}
