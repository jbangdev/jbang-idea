package dev.jbang.idea.cli

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

/**
 * Tests for [JBangCli.resolveJBangPath] — the testable overload that takes
 * injected environment values so we can simulate Windows/Unix on any OS.
 */
class JBangCliPathTest : BasePlatformTestCase() {

    private lateinit var tmpDir: File

    override fun setUp() {
        super.setUp()
        tmpDir = Files.createTempDirectory("jbang-path-test").toFile()
    }

    override fun tearDown() {
        tmpDir.deleteRecursively()
        super.tearDown()
    }

    // --- helpers ---

    /** Create an executable file (or just a regular file on Windows where canExecute is always true). */
    private fun createExe(parent: File, name: String): File {
        val f = File(parent, name)
        f.parentFile.mkdirs()
        f.writeText("stub")
        f.setExecutable(true)
        return f
    }

    // --- settings path takes priority ---

    fun testSettingsPathWins() {
        val settingsExe = createExe(tmpDir, "custom/jbang")
        val homeDir = File(tmpDir, "home")
        createExe(homeDir, ".jbang/bin/jbang")

        val result = JBangCli.resolveJBangPath(
            settingsPath = settingsExe.absolutePath,
            isWindows = false,
            jbangHome = null,
            userHome = homeDir.absolutePath,
            pathDirs = emptyList(),
        )
        assertEquals(settingsExe.absolutePath, result)
    }

    // --- JBANG_HOME takes priority over ~/.jbang ---

    fun testJbangHomeBeforeUserHome() {
        val jbangHome = File(tmpDir, "jbang-home")
        val homeDir = File(tmpDir, "home")
        val expected = createExe(jbangHome, "bin/jbang")
        createExe(homeDir, ".jbang/bin/jbang")

        val result = JBangCli.resolveJBangPath(
            settingsPath = "",
            isWindows = false,
            jbangHome = jbangHome.absolutePath,
            userHome = homeDir.absolutePath,
            pathDirs = emptyList(),
        )
        assertEquals(expected.absolutePath, result)
    }

    // --- Windows: picks jbang.cmd, not the bash script ---

    fun testWindowsPrefersCmd() {
        val homeDir = File(tmpDir, "home")
        // Create both files — bash script and .cmd
        createExe(homeDir, ".jbang/bin/jbang")
        val cmd = createExe(homeDir, ".jbang/bin/jbang.cmd")

        val result = JBangCli.resolveJBangPath(
            settingsPath = "",
            isWindows = true,
            jbangHome = null,
            userHome = homeDir.absolutePath,
            pathDirs = emptyList(),
        )
        assertEquals(cmd.absolutePath, result)
    }

    fun testWindowsJbangHomePicksCmd() {
        val jbangHome = File(tmpDir, "jbang-home")
        createExe(jbangHome, "bin/jbang")     // bash script — should be skipped
        val cmd = createExe(jbangHome, "bin/jbang.cmd")

        val result = JBangCli.resolveJBangPath(
            settingsPath = "",
            isWindows = true,
            jbangHome = jbangHome.absolutePath,
            userHome = tmpDir.absolutePath,
            pathDirs = emptyList(),
        )
        assertEquals(cmd.absolutePath, result)
    }

    // --- PATH lookup ---

    fun testPathLookupUnix() {
        val binDir = File(tmpDir, "usr-bin")
        val expected = createExe(binDir, "jbang")

        val result = JBangCli.resolveJBangPath(
            settingsPath = "",
            isWindows = false,
            jbangHome = null,
            userHome = File(tmpDir, "empty-home").absolutePath,
            pathDirs = listOf(binDir.absolutePath),
        )
        assertEquals(expected.absolutePath, result)
    }

    fun testPathLookupWindows() {
        val binDir = File(tmpDir, "usr-bin")
        createExe(binDir, "jbang")          // bash script — skipped
        val cmd = createExe(binDir, "jbang.cmd")

        val result = JBangCli.resolveJBangPath(
            settingsPath = "",
            isWindows = true,
            jbangHome = null,
            userHome = File(tmpDir, "empty-home").absolutePath,
            pathDirs = listOf(binDir.absolutePath),
        )
        assertEquals(cmd.absolutePath, result)
    }

    // --- nothing found ---

    fun testReturnsNullWhenNothingFound() {
        val result = JBangCli.resolveJBangPath(
            settingsPath = "",
            isWindows = false,
            jbangHome = null,
            userHome = File(tmpDir, "empty-home").absolutePath,
            pathDirs = emptyList(),
        )
        assertNull(result)
    }

    // --- Unix: picks extensionless jbang ---

    fun testUnixPicksExtensionless() {
        val homeDir = File(tmpDir, "home")
        val expected = createExe(homeDir, ".jbang/bin/jbang")
        createExe(homeDir, ".jbang/bin/jbang.cmd")  // should be ignored on Unix

        val result = JBangCli.resolveJBangPath(
            settingsPath = "",
            isWindows = false,
            jbangHome = null,
            userHome = homeDir.absolutePath,
            pathDirs = emptyList(),
        )
        assertEquals(expected.absolutePath, result)
    }
}
