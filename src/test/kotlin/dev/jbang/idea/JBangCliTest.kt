package dev.jbang.idea

import org.junit.Assert.assertEquals
import org.junit.Test

class JBangCliTest {

    @Test
    fun testCleanOutputWithJson() {
        val rawOutput = """
            Picked up _JAVA_OPTIONS: -Djava.net.preferIPv4Stack=true
            WARNING: An illegal reflective access operation has occurred
            {
              "dependencies": ["hello.jar"]
            }
        """.trimIndent()
        
        val expected = """
            {
              "dependencies": ["hello.jar"]
            }
        """.trimIndent()

        val actual = JBangCli.cleanOutput(rawOutput, isJson = true)
        assertEquals(expected, actual)
    }

    @Test
    fun testCleanOutputWithJsonArray() {
        val rawOutput = """
            Picked up _JAVA_OPTIONS: -XX:+PrintCommandLineFlags
            -XX:ConcGCThreads=2 -XX:G1ConcRefinementThreads=8 -XX:InitialHeapSize=536870912 
            [
              {
                "name": "agent"
              }
            ]
        """.trimIndent()
        
        val expected = """
            [
              {
                "name": "agent"
              }
            ]
        """.trimIndent()

        val actual = JBangCli.cleanOutput(rawOutput, isJson = true)
        assertEquals(expected, actual)
    }

    @Test
    fun testCleanOutputWithWarningContainingBracket() {
        val rawOutput = """
            WARNING: Illegal access [module java.base]
            {
              "success": true
            }
        """.trimIndent()
        
        val expected = """
            {
              "success": true
            }
        """.trimIndent()

        val actual = JBangCli.cleanOutput(rawOutput, isJson = true)
        assertEquals(expected, actual)
    }

    @Test
    fun testCleanOutputWithGcLogs() {
        val rawOutput = """
            Picked up _JAVA_OPTIONS: -Xlog:gc
            [0.006s][info][gc] Using G1
            [0.247s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 27M->7M(520M) 1.834ms
            {
              "success": true
            }
        """.trimIndent()
        
        val expected = """
            {
              "success": true
            }
        """.trimIndent()

        val actual = JBangCli.cleanOutput(rawOutput, isJson = true)
        assertEquals(expected, actual)
    }

    @Test
    fun testCleanOutputWithClasspath() {
        val rawOutput = """
            Picked up _JAVA_OPTIONS: -Djava.net.preferIPv4Stack=true
            WARNING: An illegal reflective access operation has occurred
            /path/to/a.jar:/path/to/b.jar
        """.trimIndent()
        
        val expected = "/path/to/a.jar:/path/to/b.jar"

        val actual = JBangCli.cleanOutput(rawOutput, isJson = false)
        assertEquals(expected, actual)
    }

    @Test
    fun testCleanOutputWithCompactJsonAndWarnings() {
        val rawOutput = """
            Picked up _JAVA_OPTIONS: -Xlog:gc
            [0.006s][info][gc] Using G1
            {"success": true}
        """.trimIndent()
        
        val expected = """{"success": true}"""

        val actual = JBangCli.cleanOutput(rawOutput, isJson = true)
        assertEquals(expected, actual)
    }

    @Test
    fun testCleanOutputWithCompactJsonArrayAndWarnings() {
        val rawOutput = """
            Picked up _JAVA_OPTIONS: -Xlog:gc
            [0.006s][info][gc] Using G1
            [{"name": "agent"}]
        """.trimIndent()
        
        val expected = """[{"name": "agent"}]"""

        val actual = JBangCli.cleanOutput(rawOutput, isJson = true)
        assertEquals(expected, actual)
    }

    @Test
    fun testCleanOutputWithDeceptiveWarningAndEmptyLines() {
        val rawOutput = "A\nB\nC\nD\nWarning { \n\n\n\n{\n  \"success\": true\n}"
        val expected = "{\n  \"success\": true\n}"
        val actual = JBangCli.cleanOutput(rawOutput, isJson = true)
        assertEquals(expected, actual) 
    }

    @Test
    fun testCleanOutputWithEmptyArray() {
        val rawOutput = "WARNING: something\n[]"
        val expected = "[]"
        val actual = JBangCli.cleanOutput(rawOutput, isJson = true)
        assertEquals(expected, actual)
    }

    @Test
    fun testCleanOutputJsonWithoutWarnings() {
        val jsonOutput = """{"key": "value"}"""
        assertEquals(jsonOutput, JBangCli.cleanOutput(jsonOutput, isJson = true))
    }

    @Test
    fun testCleanOutputClasspathWithoutWarnings() {
        val classpathOutput = "/path/to/a.jar"
        assertEquals(classpathOutput, JBangCli.cleanOutput(classpathOutput, isJson = false))
    }

    @Test
    fun testCleanOutputEmptyOrOnlyWarnings() {
        val emptyOutput = ""
        assertEquals("", JBangCli.cleanOutput(emptyOutput, isJson = true))
        assertEquals("", JBangCli.cleanOutput(emptyOutput, isJson = false))

        val onlyWarnings = """
            Picked up _JAVA_OPTIONS: -Djava.net.preferIPv4Stack=true
            WARNING: An illegal reflective access operation has occurred
        """.trimIndent()
        
        // When there is no JSON payload found, it should return the original string or fallback gracefully
        assertEquals(onlyWarnings, JBangCli.cleanOutput(onlyWarnings, isJson = true))
        // For classpath, it will return the last line, which is the last warning
        assertEquals("WARNING: An illegal reflective access operation has occurred", JBangCli.cleanOutput(onlyWarnings, isJson = false))
    }
}
