package dev.jbang.idea

import com.intellij.openapi.fileTypes.FileType
import javax.swing.Icon

/** File type for `.jbang` build files — directive-only, not Java source. */
class JBangFileType private constructor() : FileType {
    override fun getName() = "JBang"
    override fun getDescription() = "JBang build file"
    override fun getDefaultExtension() = "jbang"
    override fun getIcon(): Icon = JBangPlugin.icon16
    override fun isBinary() = false

    companion object {
        @JvmField
        val INSTANCE = JBangFileType()
    }
}
