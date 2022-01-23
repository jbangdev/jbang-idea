package dev.jbang.idea.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import dev.jbang.idea.JBANG_DECLARE
import dev.jbang.idea.JBANG_DECLARE_FULL
import dev.jbang.idea.getJBangDirective


class JBangDirectiveHighlighterAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is PsiComment) {
            val comment: String = element.text
            val directive = getJBangDirective(comment)
            if (directive != null) {
                val startOffset = element.textRange.startOffset
                var endOffset = startOffset + 2 + directive.length
                if (directive.startsWith(JBANG_DECLARE)) {
                    endOffset = startOffset + JBANG_DECLARE_FULL.length
                }
                val range = TextRange(startOffset + 2, endOffset)
                holder.newAnnotation(HighlightSeverity.INFORMATION, "JBang directive")
                    .range(range)
                    .textAttributes(DefaultLanguageHighlighterColors.MARKUP_ENTITY)
                    .create();
            }

        }

    }
}
