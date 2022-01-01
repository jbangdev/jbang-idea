package dev.jbang.idea.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import dev.jbang.idea.getJBangDirective


class JbangDirectiveHighlighterAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is PsiComment) {
            val comment: String = element.text
            val directive = getJBangDirective(comment)
            if (directive != null) {
                val startOffset = element.textRange.startOffset
                val range = TextRange(startOffset+2, startOffset + 2 + directive.length)
                holder.newAnnotation(HighlightSeverity.INFORMATION, "JBang directive")
                    .range(range)
                    .textAttributes(DefaultLanguageHighlighterColors.MARKUP_ENTITY)
                    .create();
            }

        }

    }
}
