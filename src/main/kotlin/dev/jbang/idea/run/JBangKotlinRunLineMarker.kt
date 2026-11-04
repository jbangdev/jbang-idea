package dev.jbang.idea.run

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

class JBangKotlinRunLineMarker : JBangRunLineMarker() {

    override fun isNameIdentifier(element: PsiElement): Boolean {
        val parent = element.parent
        return (parent is KtClass && element == parent.nameIdentifier) ||
            (parent is KtNamedFunction && element == parent.nameIdentifier)
    }

    override fun declarationInfo(element: PsiElement): Info? {
        val file = element.containingFile ?: return null
        val parent = element.parent

        if (parent is KtClass && element == parent.nameIdentifier) {
            if (PsiTreeUtil.findChildOfType(file, KtClass::class.java) === parent) {
                return jbangInfo(file.name, element)
            }
        }
        if (parent is KtNamedFunction && element == parent.nameIdentifier && parent.name == "main") {
            return jbangInfo(file.name, element)
        }

        return null
    }
}
