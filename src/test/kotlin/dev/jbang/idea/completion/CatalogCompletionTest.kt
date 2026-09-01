package dev.jbang.idea.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.nio.file.Path

class CatalogCompletionTest : LightJavaCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        VfsRootAccess.allowRootAccess(testRootDisposable, Path.of("").toAbsolutePath().toString())
    }

    fun testBundledCatalogSchemaDescribesCurrentJavaAndTemplateProperties() {
        val schema = com.google.gson.JsonParser.parseReader(
            java.nio.file.Files.newBufferedReader(Path.of("src/main/resources/jbang-catalog-schema.json")),
        ).asJsonObject
        val aliasProperties = schema.getAsJsonObject("properties")
            .getAsJsonObject("aliases").getAsJsonObject("additionalProperties").getAsJsonObject("properties")
        val javaVersions = aliasProperties.getAsJsonObject("java").getAsJsonArray("enum").map { it.asString }
        val templateProperties = schema.getAsJsonObject("properties")
            .getAsJsonObject("templates").getAsJsonObject("additionalProperties").getAsJsonObject("properties")

        assertTrue("Catalog schema should accept Java 25", "25" in javaVersions)
        assertTrue("Catalog schema should describe template properties", templateProperties.has("properties"))
    }

    fun testCatalogSchemaAcceptsCurrentJavaAndTemplateProperties() {
        myFixture.configureByText(
            "jbang-catalog.json",
            """{
              "aliases": {"hello": {"script-ref": "hello.java", "java": "25"}},
              "templates": {"hello": {
                "file-refs": {"hello.java": "hello.java.qute"},
                "properties": {"name": {"description": "User name", "default": "Duke"}}
              }}
            }""".trimIndent(),
        )

        val errors = myFixture.doHighlighting()
            .filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }

        assertTrue("Current JBang catalogs should satisfy the bundled schema: $errors", errors.isEmpty())
    }

    fun testCompletesLocalScriptRef() {
        myFixture.addFileToProject("scripts/hello.java", "class hello {}")
        myFixture.addFileToProject("scripts/readme.txt", "not a script")
        myFixture.configureByText(
            "jbang-catalog.json",
            """{"aliases":{"hello":{"script-ref":"scripts/<caret>"}}}""",
        )

        myFixture.complete(CompletionType.BASIC)

        assertTrue(myFixture.lookupElementStrings.orEmpty().contains("hello.java"))
        assertFalse(myFixture.lookupElementStrings.orEmpty().contains("readme.txt"))
    }
}
