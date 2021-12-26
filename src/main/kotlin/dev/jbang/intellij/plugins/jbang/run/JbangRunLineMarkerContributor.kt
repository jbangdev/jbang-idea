package dev.jbang.intellij.plugins.jbang.run

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import dev.jbang.intellij.plugins.jbang.JBANG_DECLARE
import dev.jbang.intellij.plugins.jbang.jbangIcon

class JbangRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element is PsiComment) {
            if (element.text.startsWith(JBANG_DECLARE)) {
                return Info(jbangIcon, { "Run by JBang" }, JbangRunScriptAction(element))
            }
        }
        return null
    }
}