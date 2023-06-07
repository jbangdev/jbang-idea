package dev.jbang.idea.completion.directive

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.idea.KotlinLanguage

class JBangKotlinCompletionContributor : JBangBaseDirectiveCompletionContributor(KotlinLanguage.INSTANCE) {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        super.addCompletions(parameters, context, result)
        result.addElement(LookupElementBuilder.create("KOTLIN").withTailText(" Kotlin version", true))
    }

}  