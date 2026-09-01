package dev.jbang.idea.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import dev.jbang.idea.JBangPlugin

/** Completion for common directive arguments that IntelliJ can derive without invoking JBang. */
class DirectiveArgumentCompletion : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(PsiComment::class.java),
            DirectiveArgumentCompletionProvider,
        )
    }
}

private object DirectiveArgumentCompletionProvider : CompletionProvider<CompletionParameters>() {
    private val arguments = mapOf(
        "JAVA" to listOf("8", "11", "17", "21", "25"),
        "REPOS" to listOf(
            "central", "mavencentral", "jbossorg", "redhat", "jcenter",
            "google", "jitpack", "sponge", "central-portal-snapshots",
            "sonatype-snapshots", "s01sonatype-snapshots",
            "spring-snapshot", "spring-milestone", "jogamp", "mvnpm",
        ),
    )

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val comment = (parameters.position as? PsiComment)
            ?: PsiTreeUtil.getParentOfType(parameters.position, PsiComment::class.java, false)
            ?: return
        val text = comment.text
            .replace(CompletionUtil.DUMMY_IDENTIFIER, "")
            .replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, "")
        val match = Regex("^//(JAVA|REPOS|MAIN)\\s+(.*)$").matchEntire(text) ?: return
        val directive = match.groupValues[1]
        val prefix = match.groupValues[2].substringAfterLast(' ')
        val target = result.withPrefixMatcher(prefix)

        if (directive == "MAIN") {
            PsiTreeUtil.findChildrenOfType(parameters.originalFile, PsiClass::class.java)
                .mapNotNull(PsiClass::getName)
                .distinct()
                .forEach { target.addElement(LookupElementBuilder.create(it).withIcon(JBangPlugin.icon16)) }
            return
        }

        arguments[directive].orEmpty().forEach {
            target.addElement(LookupElementBuilder.create(it).withIcon(JBangPlugin.icon16))
        }
    }
}
