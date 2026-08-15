package dev.jbang.idea

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JBangFileTypeTest : BasePlatformTestCase() {

    fun testJshFilesUseJShellFileType() {
        val type = FileTypeManager.getInstance().getFileTypeByFileName("hello.jsh")

        assertEquals("JSHELL", type.name)
    }
}
