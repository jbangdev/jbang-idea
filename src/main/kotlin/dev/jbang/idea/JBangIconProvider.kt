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

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? =
        JBangPlugin.icon16.takeIf { JBangScriptDetector.isRootScript(file) }
}
