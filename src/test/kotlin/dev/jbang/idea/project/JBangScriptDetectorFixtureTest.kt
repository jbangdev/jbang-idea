package dev.jbang.idea.project

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test

/**
 * Fixture tests for root script detection using real VirtualFile instances.
 */
class JBangScriptDetectorFixtureTest : LightJavaCodeInsightFixtureTestCase() {

    override fun getTestDataPath() = "src/test/testdata"

    @Test
    fun testDetectsRootWithShebang() {
        val file = myFixture.configureByFile("detect/root_with_shebang.java").virtualFile
        assertTrue("File with shebang should be a root", JBangScriptDetector.isRootScript(file))
    }

    @Test
    fun testDetectsRootWithDeps() {
        val file = myFixture.configureByFile("detect/root_with_deps.java").virtualFile
        assertTrue("File with //DEPS should be a root", JBangScriptDetector.isRootScript(file))
    }

    @Test
    fun testPlainJavaIsNotRoot() {
        val file = myFixture.configureByFile("detect/plain_java.java").virtualFile
        assertFalse("Plain Java file should not be a root", JBangScriptDetector.isRootScript(file))
    }

    @Test
    fun testBuildJbangIsAlwaysRoot() {
        val file = myFixture.addFileToProject("build.jbang", "//JAVA 21\n").virtualFile
        assertTrue("build.jbang should always be a root", JBangScriptDetector.isRootScript(file))
    }

    @Test
    fun testBuildJavaIsAlwaysRoot() {
        val file = myFixture.addFileToProject("build.java", "class build {}\n").virtualFile
        assertTrue("build.java should always be a root", JBangScriptDetector.isRootScript(file))
    }

    @Test
    fun testNonScriptExtensionIsNotRoot() {
        val file = myFixture.addFileToProject("readme.md", "# Hello\n").virtualFile
        assertFalse(".md file should not be a root", JBangScriptDetector.isRootScript(file))
    }

    @Test
    fun testScriptExtensionCheck() {
        val java = myFixture.addFileToProject("hello.java", "").virtualFile
        val kt = myFixture.addFileToProject("hello.kt", "").virtualFile
        val groovy = myFixture.addFileToProject("hello.groovy", "").virtualFile
        val jsh = myFixture.addFileToProject("hello.jsh", "").virtualFile
        val txt = myFixture.addFileToProject("hello.txt", "").virtualFile

        assertTrue(JBangScriptDetector.isScriptExtension(java))
        assertTrue(JBangScriptDetector.isScriptExtension(kt))
        assertTrue(JBangScriptDetector.isScriptExtension(groovy))
        assertTrue(JBangScriptDetector.isScriptExtension(jsh))
        assertFalse(JBangScriptDetector.isScriptExtension(txt))
    }
}
