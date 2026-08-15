package dev.jbang.idea.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
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
        val start = element.textRange.startOffset
        val directiveEnd = 2 + directive.length
        if (directive !in JBangPlugin.ALL_DIRECTIVES) {
            holder.newAnnotation(HighlightSeverity.WARNING, "Unknown JBang directive: $directive")
                .range(TextRange(start, start + directiveEnd))
                .create()
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
                holder.newAnnotation(HighlightSeverity.ERROR, "Invalid dependency; expected group:artifact:version")
                    .range(TextRange(start + offset, start + offset + dependency.length))
                    .create()
            } else {
                val earlierDuplicate = PsiTreeUtil.findChildrenOfType(element.containingFile, PsiComment::class.java)
                    .any { it.textRange.startOffset < start && it.text == "//DEPS $dependency" }
                if (earlierDuplicate) {
                    holder.newAnnotation(HighlightSeverity.WARNING, "Duplicate //DEPS: $dependency")
                        .range(element)
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
        return parts.size >= 3 && parts.take(3).all { it.isNotBlank() }
    }
}
