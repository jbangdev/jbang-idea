package dev.jbang.intellij.plugins.jbang

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

class JbangIconProvider : FileIconProvider {
    private val jbangIcon = IconLoader.findIcon("jbang-16x16.png")

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        return if (file.name == "jbang-catalog.json") {
            jbangIcon
        } else {
            null
        }
    }
}