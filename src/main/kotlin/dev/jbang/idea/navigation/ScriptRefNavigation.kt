package dev.jbang.idea.navigation

import com.intellij.navigation.DirectNavigationProvider
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import dev.jbang.idea.completion.ScriptRefCompletionContributor.Companion.scriptRefValueCapture
import org.jetbrains.kotlin.idea.core.util.toPsiFile


@org.jetbrains.annotations.ApiStatus.Experimental
class ScriptRefNavigation : DirectNavigationProvider {

    override fun getNavigationElement(element: PsiElement): PsiElement? {
        if (scriptRefValueCapture.accepts(element)) {
            val scriptRef = element.text.trim('"')
            if (!(scriptRef.startsWith("http://") || scriptRef.startsWith("https://"))
                && !scriptRef.startsWith("/")
            ) {
                val directory = element.containingFile.parent as PsiDirectory
                val realPath = directory.virtualFile.toNioPath().resolve(scriptRef)
                return VirtualFileManager.getInstance().findFileByNioPath(realPath)?.toPsiFile(element.project)
            }
        }
        return null
    }
}