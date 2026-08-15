package dev.jbang.idea

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.jbang.idea.project.JBangScriptDetector
import javax.swing.Icon

/**
 * Shows the JBang icon for jbang root scripts and build files.
 */
class JBangIconProvider : FileIconProvider {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file.name in JBangPlugin.BUILD_FILE_NAMES) return JBangPlugin.icon16
        if (file.extension == "jbang") return JBangPlugin.icon16
        if (JBangScriptDetector.isRootScript(file)) return JBangPlugin.icon16
        return null
    }
}
