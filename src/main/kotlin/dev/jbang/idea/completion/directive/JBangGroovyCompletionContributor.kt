package dev.jbang.idea.completion.directive

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.GroovyLanguage

class JBangGroovyCompletionContributor : JBangBaseDirectiveCompletionContributor(GroovyLanguage) {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        super.addCompletions(parameters, context, result)
        result.addElement(LookupElementBuilder.create("GROOVY").withTailText(" Groovy version", true))
    }


}  