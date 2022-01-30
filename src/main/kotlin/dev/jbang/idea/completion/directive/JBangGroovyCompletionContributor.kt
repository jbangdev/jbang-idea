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
import org.jetbrains.plugins.groovy.GroovyLanguage

class JBangGroovyCompletionContributor : JBangBaseDirectiveCompletionContributor() {
    override fun _addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext,
                                result: CompletionResultSet) {
        super._addCompletions(parameters, context, result)
        result.addElement(LookupElementBuilder.create("GROOVY").withTailText("Groovy version", true))
    }


}  