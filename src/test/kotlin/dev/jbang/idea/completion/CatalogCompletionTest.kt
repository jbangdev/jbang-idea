package dev.jbang.idea.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class CatalogCompletionTest : LightJavaCodeInsightFixtureTestCase() {

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
