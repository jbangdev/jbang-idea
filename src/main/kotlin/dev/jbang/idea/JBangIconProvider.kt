package dev.jbang.idea

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

class JBangIconProvider : FileIconProvider {
    companion object {
        val JBANG_FILES = listOf("jbang-catalog.json", "build.java", "build.kt", "build.groovy")
    }

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        return if (JBANG_FILES.contains(file.name)) {
            jbangIcon12
        } else {
            null
        }
    }
}