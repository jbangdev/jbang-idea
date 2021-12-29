package dev.jbang.idea

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

class JbangIconProvider : FileIconProvider {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        return if (file.name == "jbang-catalog.json") {
            jbangIcon
        } else {
            null
        }
    }
}