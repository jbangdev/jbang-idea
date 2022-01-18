package dev.jbang.idea.navigation

import com.intellij.json.psi.JsonStringLiteral
import com.intellij.navigation.DirectNavigationProvider
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.prevSiblingOfSameType


@org.jetbrains.annotations.ApiStatus.Experimental
class ScriptRefNavigation : DirectNavigationProvider {

    override fun getNavigationElement(element: PsiElement): PsiElement? {
        if (element is JsonStringLiteral) {
            if (element.containingFile.name == "jbang-catalog.json") {
                val siblingElement = element.prevSiblingOfSameType()
                if (siblingElement != null && siblingElement.text.contains("script-ref")) {
                    val scriptRef = element.text.trim('"')
                    if (!scriptRef.contains(":")) {
                        val directory = element.containingFile.parent as PsiDirectory
                        val realPath = directory.virtualFile.toNioPath().resolve(scriptRef)
                        return VirtualFileManager.getInstance().findFileByNioPath(realPath)?.toPsiFile(element.project)
                    }
                }
            }
        }
        return null
    }
}