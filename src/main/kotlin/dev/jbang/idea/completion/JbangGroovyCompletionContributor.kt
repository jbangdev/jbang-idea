package dev.jbang.idea.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.GroovyLanguage

class JbangGroovyCompletionContributor : JbangBaseDirectiveCompletionContributor() {
    init {
        extend(
            CompletionType.BASIC, PlatformPatterns.psiElement(PsiComment::class.java).withLanguage(GroovyLanguage),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val comment = parameters.position as PsiComment
                    val commentParent = comment.parent
                    if (commentParent is PsiFile) {
                        if (shouldCompleteForDirective(comment.text)) {
                            JAVA_DIRECTIVES.forEach {
                                result.addElement(LookupElementBuilder.create(it))
                            }
                            result.addElement(LookupElementBuilder.create("GROOVY"))
                        }
                    }
                }
            }
        )
    }

}  