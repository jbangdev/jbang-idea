package dev.jbang.idea

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JBangFileTypeTest : BasePlatformTestCase() {

    fun testJbangFilesUseOwnFileType() {
        val type = FileTypeManager.getInstance().getFileTypeByFileName("hello.jbang")

        assertEquals("JBang", type.name)
        assertFalse("jbang files should not be Java", type.name == "JAVA")
    }

    fun testJbangFileIsDetectedAsRoot() {
        val file = myFixture.addFileToProject("hello.jbang",
            "//DEPS com.google.guava:guava:33.0-jre\n//JAVA 21\n")

        assertTrue(dev.jbang.idea.project.JBangScriptDetector.isRootScript(file.virtualFile))
    }

    fun testJshFilesUseJShellFileType() {
        val type = FileTypeManager.getInstance().getFileTypeByFileName("hello.jsh")

        assertEquals("JSHELL", type.name)
    }
}
