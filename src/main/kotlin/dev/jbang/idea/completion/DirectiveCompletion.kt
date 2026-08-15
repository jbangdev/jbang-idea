package dev.jbang.idea.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.util.ProcessingContext
import dev.jbang.idea.JBangPlugin

/**
 * Single completion contributor for all jbang directives.
 * Registered once for JAVA — works for comments in Java files.
 * Kotlin and Groovy get their own registrations in plugin.xml pointing here.
 *
 * Completes `//` → `//DEPS`, `//JAVA`, etc.
 */
class DirectiveCompletion : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(PsiComment::class.java),
            DirectiveCompletionProvider
        )
    }
}

private object DirectiveCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position
        val text = element.text

        // Directive names only complete before their first argument.
        if (!text.startsWith("//") || text.takeWhile { it != '\u0000' }.contains(' ')) return

        // Extract what's after // to filter
        val prefix = text.substring(2).takeWhile { it.isLetter() || it == '_' || it == '\u0000' }
            .replace("\u0000", "") // IntelliJ dummy identifier

        val completionPrefix = "//$prefix"
        val newResult = result.withPrefixMatcher(completionPrefix)

        for ((directive, description) in JBangPlugin.ALL_DIRECTIVES) {
            newResult.addElement(
                LookupElementBuilder.create("//$directive ")
                    .withPresentableText("//$directive")
                    .withTailText("  $description", true)
                    .withIcon(JBangPlugin.icon16)
                    .bold()
            )
        }
    }
}
