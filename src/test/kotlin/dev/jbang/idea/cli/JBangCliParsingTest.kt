package dev.jbang.idea.cli

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for JSON parsing of jbang CLI output.
 * No IntelliJ fixture, no jbang binary needed — just Gson + data classes.
 */
class JBangCliParsingTest {

    private val gson = Gson()

    @Test
    fun `parse minimal script info`() {
        val json = """
        {
          "originalResource": "/tmp/hello.java",
          "backingResource": "/tmp/hello.java",
          "resolvedDependencies": [],
          "javaVersion": "21",
          "sources": [
            {"originalResource": "/tmp/hello.java", "backingResource": "/tmp/hello.java"}
          ]
        }
        """.trimIndent()

        val info = gson.fromJson(json, ScriptInfo::class.java)
        assertEquals("/tmp/hello.java", info.originalResource)
        assertEquals("21", info.javaVersion)
        assertTrue(info.resolvedDependencies.isEmpty())
        assertEquals(1, info.sources.size)
        assertEquals("/tmp/hello.java", info.sources[0].originalResource)
    }

    @Test
    fun `parse script info with dependencies`() {
        val json = """
        {
          "originalResource": "/tmp/app.java",
          "backingResource": "/tmp/app.java",
          "applicationJar": "/home/.jbang/cache/jars/app.jar",
          "dependencies": ["com.google.guava:guava:33.0-jre"],
          "resolvedDependencies": [
            "/home/.m2/repository/com/google/guava/guava/33.0-jre/guava-33.0-jre.jar",
            "/home/.m2/repository/com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2.jar"
          ],
          "javaVersion": "21",
          "requestedJavaVersion": "21",
          "availableJdkPath": "/home/.jbang/cache/jdks/21",
          "compileOptions": ["-g", "-parameters"],
          "sources": [
            {"originalResource": "/tmp/app.java", "backingResource": "/tmp/app.java"},
            {"originalResource": "/tmp/Util.java", "backingResource": "/tmp/Util.java"}
          ]
        }
        """.trimIndent()

        val info = gson.fromJson(json, ScriptInfo::class.java)
        assertEquals(1, info.dependencies.size)
        assertEquals("com.google.guava:guava:33.0-jre", info.dependencies[0])
        assertEquals(2, info.resolvedDependencies.size)
        assertTrue(info.resolvedDependencies[0].endsWith("guava-33.0-jre.jar"))
        assertEquals(2, info.classpathJars.size)
        assertEquals("21", info.requestedJavaVersion)
        assertEquals("/home/.jbang/cache/jdks/21", info.availableJdkPath)
        assertEquals(2, info.compileOptions.size)
        assertEquals(2, info.sources.size)
    }

    @Test
    fun `missing resolved dependencies reports each coordinate`() {
        val info = ScriptInfo(dependencies = listOf("example:missing:99"))

        assertEquals(listOf("Unable to resolve dependency: example:missing:99"), info.resolutionErrors)
    }

    @Test
    fun `classpathJars falls back to applicationJar when no resolved deps`() {
        val json = """
        {
          "originalResource": "/tmp/hello.java",
          "applicationJar": "/cache/hello.jar",
          "resolvedDependencies": [],
          "sources": []
        }
        """.trimIndent()

        val info = gson.fromJson(json, ScriptInfo::class.java)
        assertEquals(1, info.classpathJars.size)
        assertEquals("/cache/hello.jar", info.classpathJars[0])
    }

    @Test
    fun `parse files entry with resolution error`() {
        val json = """
        {
          "originalResource": "/tmp/hello.java",
          "files": [
            {
              "originalResource": "wonka",
              "target": "project.iml",
              "error": "not resolvable from resource chain"
            }
          ]
        }
        """.trimIndent()

        val info = gson.fromJson(json, ScriptInfo::class.java)
        assertEquals("wonka", info.files.single().originalResource)
        assertEquals("project.iml", info.files.single().target)
        assertEquals("not resolvable from resource chain", info.files.single().error)
        assertEquals(listOf("not resolvable from resource chain"), info.resolutionErrors)
    }

    @Test
    fun `parse source entry with error`() {
        val json = """
        {
          "originalResource": "/tmp/hello.java",
          "sources": [
            {"originalResource": "/tmp/hello.java", "backingResource": "/tmp/hello.java"},
            {"originalResource": "<caret>", "error": "not found"}
          ]
        }
        """.trimIndent()

        val info = gson.fromJson(json, ScriptInfo::class.java)
        assertEquals(2, info.sources.size)
        assertNull(info.sources[0].error)
        assertEquals("not found", info.sources[1].error)
        assertEquals(listOf("not found"), info.resolutionErrors)
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = """
        {
          "originalResource": "/tmp/hello.java",
          "someNewField": "value",
          "docs": {"readme": "hello"},
          "resolvedDependencies": [],
          "sources": []
        }
        """.trimIndent()

        // Should not throw
        val info = gson.fromJson(json, ScriptInfo::class.java)
        assertEquals("/tmp/hello.java", info.originalResource)
    }
}
