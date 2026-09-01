package dev.jbang.idea.highlight

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import dev.jbang.idea.JBangPlugin
import dev.jbang.idea.project.JBangProjectService

/**
 * Highlights jbang directive comments (//DEPS, //JAVA, etc.)
 * with keyword-style coloring instead of plain comment gray.
 */
class JBangDirectiveAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiComment) return

        val text = element.text
        if (!text.startsWith("//")) return

        val directive = text.substring(2).takeWhile { it.isLetter() || it == '_' }
        if (directive.isEmpty()) return

        // JBang directives must start at column 0 — skip indented comments
        val doc = element.containingFile?.viewProvider?.document
        if (doc != null) {
            val lineNum = doc.getLineNumber(element.textRange.startOffset)
            if (element.textRange.startOffset != doc.getLineStartOffset(lineNum)) return
        }
        // Skip all-lowercase words — regular comments like //noinspection, //region, etc.
        if (directive == directive.lowercase()) return
        val start = element.textRange.startOffset
        val directiveEnd = 2 + directive.length
        if (directive !in JBangPlugin.ALL_DIRECTIVES) {
            // Check if it's a known directive with wrong casing (e.g. //Deps)
            val upper = directive.uppercase()
            if (upper in JBangPlugin.ALL_DIRECTIVES) {
                holder.newAnnotation(HighlightSeverity.WARNING, "JBang directive should be uppercase: //$upper")
                    .range(TextRange(start, start + directiveEnd))
                    .withFix(UppercaseDirectiveFix(element, upper))
                    .create()
            } else if (directive == upper) {
                // All uppercase but unknown — likely a typo
                holder.newAnnotation(HighlightSeverity.WARNING, "Unknown JBang directive: $directive")
                    .range(TextRange(start, start + directiveEnd))
                    .create()
            }
            // Mixed case that doesn't match any known directive — ignore (e.g. //SuppressWarnings)
            return
        }

        // Highlight the //DIRECTIVE part as a keyword
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(start, start + directiveEnd))
            .enforcedTextAttributes(DefaultLanguageHighlighterColors.KEYWORD.defaultAttributes)
            .create()

        val argument = text.substring(directiveEnd).trim()
        if (directive == "DEPS" && argument.isNotEmpty()) {
            val dependency = argument.substringBefore(' ')
            if (!dependency.isValidDependency()) {
                val offset = text.indexOf(dependency, directiveEnd)
                holder.newAnnotation(HighlightSeverity.ERROR, "Invalid dependency; expected group:artifact[:version]")
                    .range(TextRange(start + offset, start + offset + dependency.length))
                    .create()
            } else {
                val earlierDuplicate = PsiTreeUtil.findChildrenOfType(element.containingFile, PsiComment::class.java)
                    .any { it.textRange.startOffset < start && it.text == "//DEPS $dependency" }
                if (earlierDuplicate) {
                    holder.newAnnotation(HighlightSeverity.WARNING, "Duplicate //DEPS: $dependency")
                        .range(element)
                        .withFix(RemoveDuplicateDependencyFix(element))
                        .create()
                }
            }
        }

        val info = JBangProjectService.getInstance(element.project).getInfo(element.containingFile.virtualFile?.path ?: return)
            ?: return
        val errors = when (directive) {
            "DEPS" -> argument.substringBefore(' ').let { dependency ->
                (info.commandErrors + info.dependencyErrors)
                    .filter { dependency.isNotEmpty() && dependency in it }
                    .map { dependency to it }
            }
            "SOURCES" -> info.sources.mapNotNull { entry -> entry.error?.let { entry.originalResource to it } }
            "FILES" -> info.files.mapNotNull { entry -> entry.error?.let { entry.originalResource to it } }
            else -> emptyList()
        }
        if (text.length <= directiveEnd) return
        val argumentStart = directiveEnd + 1
        val arguments = text.substring(argumentStart).splitToSequence(Regex("\\s+"))
        for ((resource, error) in errors) {
            val token = arguments.firstOrNull {
                (if (directive == "FILES") it.substringAfter('=', it) else it) == resource
            } ?: continue
            val source = if (directive == "FILES") token.substringAfter('=', token) else token
            val offset = text.indexOf(source, argumentStart + token.indexOf(source))
            holder.newAnnotation(HighlightSeverity.ERROR, error)
                .range(TextRange(start + offset, start + offset + source.length))
                .create()
        }
    }

    private fun String.isValidDependency(): Boolean {
        if (startsWith("/") || startsWith("./") || startsWith("../") || endsWith(".jar")) return true
        val parts = split(':')
        return parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank() &&
            (parts.size == 2 || parts[2].isNotBlank())
    }
}

private abstract class JBangDirectiveFix(element: PsiElement) : IntentionAction {
    protected val pointer: SmartPsiElementPointer<PsiElement> =
        SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)

    override fun getFamilyName() = "JBang directives"
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = pointer.element?.isValid == true
    override fun startInWriteAction() = true
}

private class UppercaseDirectiveFix(element: PsiElement, private val directive: String) : JBangDirectiveFix(element) {
    override fun getText() = "Change to //$directive"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val comment = pointer.element ?: return
        val document = comment.containingFile.viewProvider.document ?: return
        document.replaceString(comment.textRange.startOffset, comment.textRange.startOffset + 2 + directive.length, "//$directive")
    }
}

private class RemoveDuplicateDependencyFix(element: PsiElement) : JBangDirectiveFix(element) {
    override fun getText() = "Remove duplicate dependency"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val comment = pointer.element ?: return
        val document = comment.containingFile.viewProvider.document ?: return
        val start = comment.textRange.startOffset
        var end = comment.textRange.endOffset
        // Consume the trailing newline so we don't leave a blank line
        if (end < document.textLength && document.charsSequence[end] == '\n') end++
        document.deleteString(start, end)
    }
}
