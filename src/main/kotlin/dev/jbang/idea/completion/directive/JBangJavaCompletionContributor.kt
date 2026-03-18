package dev.jbang.idea.completion.directive

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.java.JavaLanguage
import com.intellij.util.ProcessingContext


class JBangJavaCompletionContributor : JBangBaseDirectiveCompletionContributor(JavaLanguage.INSTANCE) {

    override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
    ) {
        super.addCompletions(parameters, context, result)
        result.addElement(LookupElementBuilder.create("PREVIEW").withTailText(" --enable-preview flag", true))
        result.addElement(LookupElementBuilder.create("MODULE").withTailText(" treat the code a being a module", true))
    }

}  