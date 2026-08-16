package dev.jbang.idea.navigation

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test

class JBangNavigationTest : LightJavaCodeInsightFixtureTestCase() {

    @Test
    fun testSourcesPathResolvesRelativeToScript() {
        val target = myFixture.addFileToProject("helpers/AHelper.java", "class AHelper {}")
        myFixture.configureByText("Root.java", "//SOURCES helpers/A<caret>Helper.java\nclass Root {}")

        val resolved = myFixture.file.findReferenceAt(myFixture.caretOffset)!!.resolve()

        assertEquals(target.virtualFile, resolved!!.containingFile.virtualFile)
    }

    @Test
    fun testFilesPathResolvesRelativeToScript() {
        val target = myFixture.addFileToProject("config/app.json", "{}")
        myFixture.configureByText("Root.java", "//FILES config/a<caret>pp.json\nclass Root {}")

        val resolved = myFixture.file.findReferenceAt(myFixture.caretOffset)!!.resolve()

        assertEquals(target.virtualFile, resolved!!.containingFile.virtualFile)
    }

    @Test
    fun testMappedFilesPathResolvesSourceSide() {
        val target = myFixture.addFileToProject("config/app.json", "{}")
        myFixture.configureByText("Root.java", "//FILES mounted.json=config/a<caret>pp.json\nclass Root {}")

        val resolved = myFixture.file.findReferenceAt(myFixture.caretOffset)!!.resolve()

        assertEquals(target.virtualFile, resolved!!.containingFile.virtualFile)
    }

    @Test
    fun testMultipleFilesPathsResolveIndependently() {
        val target = myFixture.addFileToProject("config/second.json", "{}")
        myFixture.addFileToProject("config/first.json", "{}")
        myFixture.configureByText(
            "Root.java",
            "//FILES config/first.json mounted.json=config/sec<caret>ond.json\nclass Root {}",
        )

        val resolved = myFixture.file.findReferenceAt(myFixture.caretOffset)!!.resolve()

        assertEquals(target.virtualFile, resolved!!.containingFile.virtualFile)
    }

    @Test
    fun testCatalogScriptRefResolvesRelativeToCatalog() {
        val target = myFixture.addFileToProject("scripts/hello.java", "class hello {}")
        myFixture.configureByText("jbang-catalog.json", """
            {"aliases":{"hello":{"script-ref":"scripts/he<caret>llo.java"}}}
        """.trimIndent())

        val resolved = myFixture.file.findReferenceAt(myFixture.caretOffset)!!.resolve()

        assertEquals(target.virtualFile, resolved!!.containingFile.virtualFile)
    }

    @Test
    fun testMissingTargetIsSoftAndDoesNotResolve() {
        myFixture.configureByText("Root.java", "//SOURCES missing/No<caret>pe.java\nclass Root {}")

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)

        assertNotNull(reference)
        assertTrue(reference!!.isSoft)
        assertNull(reference.resolve())
    }
}
