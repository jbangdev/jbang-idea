package dev.jbang.intellij.plugins.jbang.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.java.JavaLanguage
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext


class JbangJavaCompletionContributor : JbangBaseDirectiveCompletionContributor() {
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
                    val commentParent = comment.parent
                    if (commentParent is PsiClass || commentParent is PsiFile) {
                        if (comment.text.startsWith("//")) {
                            JAVA_DIRECTIVES.forEach {
                                result.addElement(LookupElementBuilder.create(it))
                            }
                        }
                    }
                }
            }
        )
    }

}  