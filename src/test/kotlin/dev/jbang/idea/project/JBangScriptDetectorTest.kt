package dev.jbang.idea.project

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for directive/root detection logic.
 * No IntelliJ fixture needed — pure string parsing.
 */
class JBangScriptDetectorTest {

    @Test
    fun `shebang line is a directive`() {
        assertTrue(JBangScriptDetector.isDirectiveLine("///usr/bin/env jbang"))
    }

    @Test
    fun `DEPS is a directive`() {
        assertTrue(JBangScriptDetector.isDirectiveLine("//DEPS com.google.guava:guava:33.0-jre"))
    }

    @Test
    fun `JAVA is a directive`() {
        assertTrue(JBangScriptDetector.isDirectiveLine("//JAVA 21"))
    }

    @Test
    fun `SOURCES is a directive`() {
        assertTrue(JBangScriptDetector.isDirectiveLine("//SOURCES **/*.java"))
    }

    @Test
    fun `FILES is a directive`() {
        assertTrue(JBangScriptDetector.isDirectiveLine("//FILES data.json"))
    }

    @Test
    fun `REPOS is a directive`() {
        assertTrue(JBangScriptDetector.isDirectiveLine("//REPOS mavencentral"))
    }

    @Test
    fun `COMPILE_OPTIONS is a directive`() {
        assertTrue(JBangScriptDetector.isDirectiveLine("//COMPILE_OPTIONS --enable-preview"))
    }

    @Test
    fun `regular comment is not a directive`() {
        assertFalse(JBangScriptDetector.isDirectiveLine("// this is a comment"))
    }

    @Test
    fun `import is not a directive`() {
        assertFalse(JBangScriptDetector.isDirectiveLine("import java.util.List;"))
    }

    @Test
    fun `empty line is not a directive`() {
        assertFalse(JBangScriptDetector.isDirectiveLine(""))
    }

    @Test
    fun `unknown directive is not detected`() {
        assertFalse(JBangScriptDetector.isDirectiveLine("//FOOBAR something"))
    }

    // --- Root vs source directive ---

    @Test
    fun `DEPS is a root directive`() {
        assertTrue(JBangScriptDetector.isRootDirectiveLine("//DEPS io.quarkus:quarkus-bom:3.0@pom"))
    }

    @Test
    fun `JAVA is a root directive`() {
        assertTrue(JBangScriptDetector.isRootDirectiveLine("//JAVA 21"))
    }

    @Test
    fun `SOURCES is NOT a root directive`() {
        assertFalse(JBangScriptDetector.isRootDirectiveLine("//SOURCES util/*.java"))
    }

    @Test
    fun `FILES is NOT a root directive`() {
        assertFalse(JBangScriptDetector.isRootDirectiveLine("//FILES config.yaml"))
    }

    @Test
    fun `GAV is a root directive`() {
        assertTrue(JBangScriptDetector.isRootDirectiveLine("//GAV dev.jbang:myapp:1.0"))
    }

    @Test
    fun `PREVIEW is a root directive`() {
        assertTrue(JBangScriptDetector.isRootDirectiveLine("//PREVIEW"))
    }
}
