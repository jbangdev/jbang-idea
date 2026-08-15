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
