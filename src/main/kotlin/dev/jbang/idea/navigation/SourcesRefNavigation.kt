package dev.jbang.idea.navigation

import com.intellij.navigation.DirectNavigationProvider
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import dev.jbang.idea.isJBangScriptFile


@Suppress("UnstableApiUsage")
class SourcesRefNavigation : DirectNavigationProvider {

    @Suppress("DuplicatedCode")
    override fun getNavigationElement(element: PsiElement): PsiElement? {
        if (element is PsiComment) {
            if (isJBangScriptFile(element.containingFile.name)) {
                val comment = element.text
                if (comment.startsWith("//SOURCES ")) {
                    val sourceFile = comment.substring(comment.indexOf(' ') + 1).trim()
                    val parts = sourceFile.split("[/\\\\]".toRegex())
                    val currentFile = element.containingFile.originalFile
                    var destDir = currentFile.parent
                    if (parts.size > 1) {
                        for (item in parts.subList(0, parts.size - 1)) {
                            if (item == "..") {
                                destDir = destDir?.parentDirectory
                            } else if (item != ".") {
                                destDir = destDir?.findSubdirectory(item)
                            }
                        }
                    }
                    if (destDir != null) {
                        return destDir.findFile(parts.last())
                    }
                }
            }
        }
        return null
    }
}