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
    fun `parse template properties with descriptions and defaults`() {
        val json = """
        [
          {
            "name": "service",
            "fullName": "service@acme",
            "description": "Service template",
            "properties": {
              "region": {
                "description": "Deployment region",
                "default": "eu-central-1"
              },
              "native": {
                "description": "Build a native executable",
                "default": "false"
              }
            }
          }
        ]
        """.trimIndent()

        val template = gson.fromJson(json, Array<TemplateInfo>::class.java).single()

        assertEquals("service@acme", template.fullName)
        assertEquals("Deployment region", template.properties.getValue("region").description)
        assertEquals("eu-central-1", template.properties.getValue("region").defaultValue)
        assertEquals("false", template.properties.getValue("native").defaultValue)
    }

    @Test
    fun `template list requests declared properties`() {
        assertEquals(
            listOf("jbang", "template", "list", "--show-properties", "--format=json"),
            JBangCli.buildTemplateListCommand(),
        )
    }

    @Test
    fun `qualified template lookup targets its catalog`() {
        assertEquals(
            listOf(
                "jbang", "template", "list", "nandorholozsnyak/jbang-cloud",
                "--show-properties", "--show-origin", "--format=json",
            ),
            JBangCli.buildTemplateLookupCommand("q-aws-lambda-sqs-tf@nandorholozsnyak/jbang-cloud"),
        )
    }

    @Test
    fun `catalog response resolves qualified template with properties and origin`() {
        val json = """
        [
          {
            "name": "nandorholozsnyak/jbang-cloud",
            "resourceRef": "https://github.com/nandorholozsnyak/jbang-cloud/blob/HEAD/jbang-catalog.json",
            "templates": [
              {
                "name": "q-aws-lambda-sqs-tf",
                "catalogName": "nandorholozsnyak/jbang-cloud",
                "fullName": "q-aws-lambda-sqs-tf@nandorholozsnyak/jbang-cloud",
                "properties": {
                  "aws-sqs-enabled": {
                    "description": "Generate an SQS queue",
                    "default": "true"
                  }
                }
              }
            ]
          }
        ]
        """.trimIndent()

        val result = JBangCli.parseTemplateLookup(
            "q-aws-lambda-sqs-tf@nandorholozsnyak/jbang-cloud",
            json,
        )

        assertEquals("q-aws-lambda-sqs-tf@nandorholozsnyak/jbang-cloud", result.template?.fullName)
        assertEquals("true", result.template?.properties?.get("aws-sqs-enabled")?.defaultValue)
        assertEquals("nandorholozsnyak/jbang-cloud", result.catalogName)
        assertEquals(
            "https://github.com/nandorholozsnyak/jbang-cloud/blob/HEAD/jbang-catalog.json",
            result.catalogRef,
        )
    }

    @Test
    fun `init arguments include template property overrides`() {
        assertEquals(
            listOf(
                "jbang", "init",
                "-Dregion=us-east-1", "-Dnative=true",
                "--template", "service@acme",
                "/tmp/MyService.java",
            ),
            JBangCli.buildInitCommand(
                "/tmp/MyService.java",
                "service@acme",
                linkedMapOf("region" to "us-east-1", "native" to "true"),
            ),
        )
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
