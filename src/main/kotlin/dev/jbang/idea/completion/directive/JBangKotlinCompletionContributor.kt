package dev.jbang.idea.completion.directive

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

class JBangKotlinCompletionContributor : JBangBaseDirectiveCompletionContributor() {
    init {
        extend(CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiComment::class.java).withLanguage(KotlinLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet
                ) {
                    val comment = parameters.position as PsiComment
                    val commentParent = comment.parent
                    if (commentParent is KtNamedFunction || commentParent is KtClass || commentParent is PsiFile) {
                        if (shouldCompleteForDirective(comment.text)) {
                            JAVA_DIRECTIVES.forEach {
                                result.addElement(LookupElementBuilder.create(it))
                            }
                            result.addElement(LookupElementBuilder.create("KOTLIN "))
                        }
                    }
                }
            })
    }

}  