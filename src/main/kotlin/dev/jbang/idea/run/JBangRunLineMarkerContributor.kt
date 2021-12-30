package dev.jbang.idea.run

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import dev.jbang.idea.JBANG_DECLARE
import dev.jbang.idea.jbangIcon

class JBangRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element is PsiComment) {
            var info: Info? = null
            val comment = element.text
            if (comment.startsWith(JBANG_DECLARE)) {
                info = Info(jbangIcon, { "Run by JBang" }, JBangRunScriptAction(element))
            } else {
                if (comment.startsWith("//JAVA ")) {
                    if (!element.parent.text.startsWith(JBANG_DECLARE)) {
                        info = Info(jbangIcon, { "Run by JBang" }, JBangRunScriptAction(element))
                    }
                } else if (comment.startsWith("//DEPS ")) {
                    val scriptText = element.parent.text
                    if (!scriptText.startsWith(JBANG_DECLARE)) {
                        val lines = scriptText.lines()
                        val javaDirectiveAvailable = lines.any { it.startsWith("//JAVA ") }
                        if (!javaDirectiveAvailable) {
                            val firstDeps = lines.first { it.startsWith("//DEPS ") }
                            if (comment.trim() == firstDeps) {
                                info = Info(jbangIcon, { "Run by JBang" }, JBangRunScriptAction(element))
                            }
                        }
                    }
                }
            }
            return info
        }
        return null
    }
}