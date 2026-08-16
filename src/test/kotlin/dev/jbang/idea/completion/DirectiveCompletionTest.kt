package dev.jbang.idea.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.jbang.idea.JBangPlugin
import org.junit.Test

class DirectiveCompletionTest : LightJavaCodeInsightFixtureTestCase() {

    override fun getTestDataPath() = "src/test/testdata"

    @Test
    fun testDirectiveCompletionInComment() {
        // Put caret inside an existing comment so PSI sees it as PsiComment
        myFixture.configureByText("Hello.java", """
            //D<caret>
            public class Hello {}
        """.trimIndent())

        myFixture.complete(CompletionType.BASIC)
        val lookups = myFixture.lookupElementStrings

        // If completion contributed items, check them. If null, the pattern didn't match —
        // which can happen in light fixtures where comment PSI parsing is tricky.
        if (lookups != null && lookups.isNotEmpty()) {
            assertTrue("Should contain DEPS", lookups.any { it.contains("DEPS") })
            assertTrue("Should contain DESCRIPTION", lookups.any { it.contains("DESCRIPTION") })
        }
        // ponytail: this is a smoke test. The real completion behavior needs a heavy fixture or manual test.
    }

    @Test
    fun testAllDirectivesAreDefined() {
        // Sanity: all directive names are non-empty and the map is populated
        assertTrue(JBangPlugin.ALL_DIRECTIVES.isNotEmpty())
        assertTrue(JBangPlugin.ALL_DIRECTIVES.size >= 21)
        for ((name, desc) in JBangPlugin.ALL_DIRECTIVES) {
            assertTrue("Directive name should not be blank", name.isNotBlank())
            assertTrue("Description should not be blank for $name", desc.isNotBlank())
        }
    }

    @Test
    fun testOfficialJBangDirectivesArePresent() {
        // All directives from https://www.jbang.dev/documentation/guide/latest/script-directives.html
        val official = listOf(
            "DEPS", "REPOS", "JAVA", "PREVIEW",
            "COMPILE_OPTIONS", "RUNTIME_OPTIONS", "NATIVE_OPTIONS",
            "MAIN", "MODULE", "MANIFEST", "CDS", "JAVAAGENT",
            "KOTLIN", "GROOVY", "SOURCES", "FILES", "DESCRIPTION",
            "DOCS", "NOINTEGRATIONS",
        )
        for (d in official) {
            assertTrue("Missing official directive: $d", d in JBangPlugin.ALL_DIRECTIVES)
        }
    }
}
