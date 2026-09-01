package dev.jbang.idea.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test

class PathCompletionTest : LightJavaCodeInsightFixtureTestCase() {

    @Test
    fun testSourcesCompletesSourceFilesAndDirectories() {
        myFixture.addFileToProject("helpers/AHelper.java", "class AHelper {}")
        myFixture.addFileToProject("notes.txt", "not a source")
        val root = myFixture.addFileToProject("Root.java", "//SOURCES <caret>\nclass Root {}")
        myFixture.configureFromExistingVirtualFile(root.virtualFile)

        myFixture.complete(CompletionType.BASIC)
        val lookups = myFixture.lookupElementStrings.orEmpty()

        assertTrue("Should offer source directories", "helpers/" in lookups)
        assertFalse("Should not offer arbitrary files for SOURCES", "notes.txt" in lookups)
    }

    @Test
    fun testSourcesCompletesInsideRelativeDirectory() {
        myFixture.addFileToProject("helpers/AHelper.java", "class AHelper {}")
        myFixture.addFileToProject("helpers/data.json", "{}")
        val root = myFixture.addFileToProject("Root.java", "//SOURCES helpers/A<caret>\nclass Root {}")
        myFixture.configureFromExistingVirtualFile(root.virtualFile)

        myFixture.complete(CompletionType.BASIC)
        val lookups = myFixture.lookupElementStrings.orEmpty()

        assertTrue("Should offer Java files in the selected directory: $lookups", "AHelper.java" in lookups)
        assertFalse("Should filter non-source files", "data.json" in lookups)
    }

    @Test
    fun testSourcesCompletesAfterEarlierPath() {
        myFixture.addFileToProject("helpers/A.java", "class A {}")
        myFixture.addFileToProject("helpers/B.java", "class B {}")
        val root = myFixture.addFileToProject(
            "Root.java",
            "//SOURCES helpers/A.java helpers/B<caret>\nclass Root {}",
        )
        myFixture.configureFromExistingVirtualFile(root.virtualFile)

        myFixture.complete(CompletionType.BASIC)

        assertTrue("Should complete the current path token", "B.java" in myFixture.lookupElementStrings.orEmpty())
    }

    @Test
    fun testFilesCompletesSourceAfterTargetMappingWithoutDirectiveSuggestions() {
        myFixture.addFileToProject("jbang-multi-root.txt", "payload")
        val root = myFixture.addFileToProject("Root.java", "//FILES somepath=jbang-mu<caret>\nclass Root {}")
        myFixture.configureFromExistingVirtualFile(root.virtualFile)

        myFixture.complete(CompletionType.BASIC)
        val lookups = myFixture.lookupElementStrings.orEmpty()

        assertTrue("Should complete the source side after =: $lookups", "jbang-multi-root.txt" in lookups)
        assertFalse("Directive completion must stop after directive arguments begin", lookups.any { it.startsWith("//FILES") })
        myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR)
        assertTrue(myFixture.file.text.contains("//FILES somepath=jbang-multi-root.txt"))
    }

    @Test
    fun testFilesCompletesFilesAndDirectories() {
        myFixture.addFileToProject("config/app.json", "{}")
        myFixture.addFileToProject("readme.txt", "hello")
        val root = myFixture.addFileToProject("Root.java", "//FILES <caret>\nclass Root {}")
        myFixture.configureFromExistingVirtualFile(root.virtualFile)

        myFixture.complete(CompletionType.BASIC)
        val lookups = myFixture.lookupElementStrings.orEmpty()

        assertTrue("Should offer directories", "config/" in lookups)
        assertTrue("Should offer arbitrary files", "readme.txt" in lookups)
        assertFalse("Should not offer the script itself", "Root.java" in lookups)
    }
}
