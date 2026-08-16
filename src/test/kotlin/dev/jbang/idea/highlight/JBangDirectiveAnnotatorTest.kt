package dev.jbang.idea.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.jbang.idea.JBangPlugin
import dev.jbang.idea.cli.ResourceEntry
import dev.jbang.idea.cli.ScriptInfo
import dev.jbang.idea.project.JBangProjectService
import dev.jbang.idea.settings.JBangSettings
import org.junit.Test

class JBangDirectiveAnnotatorTest : LightJavaCodeInsightFixtureTestCase() {

    override fun getTestDataPath() = "src/test/testdata"

    private var previousAutoSync = true

    override fun setUp() {
        super.setUp()
        previousAutoSync = JBangSettings.instance.autoSync
        JBangSettings.instance.autoSync = false
    }

    override fun tearDown() {
        JBangSettings.instance.autoSync = previousAutoSync
        super.tearDown()
    }

    private fun safeHighlight(): List<HighlightInfo> {
        com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        return myFixture.doHighlighting()
    }

    @Test
    fun testDirectivesGetAnnotated() {
        myFixture.configureByFile("highlight/directives.java")
        val highlights = safeHighlight()

        val annotatedTexts = highlights
            .filter { it.forcedTextAttributes != null }
            .map { it.text }

        assertTrue("//JAVA should be highlighted", annotatedTexts.any { it.startsWith("//JAVA") })
        assertTrue("//DEPS should be highlighted", annotatedTexts.any { it.startsWith("//DEPS") })
        assertTrue("//SOURCES should be highlighted", annotatedTexts.any { it.startsWith("//SOURCES") })
        assertTrue("//FILES should be highlighted", annotatedTexts.any { it.startsWith("//FILES") })
    }

    @Test
    fun testAnnotatorHandlesShebangWithoutCrashing() {
        val psiFile = myFixture.configureByText("WithShebang.java", """
            ///usr/bin/env jbang "${'$'}0" "${'$'}@" ; exit ${'$'}?
            //DEPS com.google.guava:guava:33.0-jre
            //JAVA 21
            public class WithShebang {
                public static void main(String[] args) {}
            }
        """.trimIndent())

        val annotator = JBangDirectiveAnnotator()
        val allElements = mutableListOf<PsiElement>()
        psiFile.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                allElements.add(element)
                super.visitElement(element)
            }
        })

        val depsComment = allElements.filterIsInstance<PsiComment>()
            .find { it.text.startsWith("//DEPS") }
        assertNotNull("//DEPS should be parseable even with shebang on first line", depsComment)

        val javaComment = allElements.filterIsInstance<PsiComment>()
            .find { it.text.startsWith("//JAVA") }
        assertNotNull("//JAVA should be parseable even with shebang on first line", javaComment)
    }

    @Test
    fun testFilesResolutionErrorHighlightsFailingPath() {
        val file = myFixture.configureByText("FilesError.java", "//DEPS example:test:1\n//FILES wonka jbang-multi-root.iml\nclass FilesError {}")
        JBangProjectService.getInstance(project).cacheResolved(
            file.virtualFile.path,
            ScriptInfo(files = listOf(ResourceEntry(
                originalResource = "wonka",
                target = "jbang-multi-root.iml",
                error = "not resolvable from resource chain",
            ))),
            emptyList(),
        )

        val cached = JBangProjectService.getInstance(project).getInfo(file.virtualFile.path)
        assertEquals("not resolvable from resource chain", cached!!.files.single().error)
        val comment = PsiTreeUtil.findChildrenOfType(file, PsiComment::class.java).single { it.text.startsWith("//FILES") }
        val holder = com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl(
            com.intellij.lang.annotation.AnnotationSession(file), false
        )
        holder.runAnnotatorWithContext(comment, JBangDirectiveAnnotator())

        assertTrue("Failing //FILES resource should be highlighted", holder.any {
            it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR &&
                it.message == "not resolvable from resource chain" &&
                file.text.substring(it.startOffset, it.endOffset) == "wonka"
        })
    }

    @Test
    fun testMappedFilesErrorHighlightsMissingSourceAfterEqualsOnly() {
        val file = myFixture.configureByText(
            "MappedFilesError.java",
            "//DEPS example:test:1\n//FILES jbang-multi-root.iml=jbang-mu\n//FILES jbang-multi-root.iml\nclass MappedFilesError {}"
        )
        JBangProjectService.getInstance(project).cacheResolved(
            file.virtualFile.path,
            ScriptInfo(files = listOf(
                ResourceEntry("jbang-mu", target = "jbang-multi-root.iml", error = "not resolvable"),
                ResourceEntry("jbang-multi-root.iml", backingResource = "/tmp/jbang-multi-root.iml"),
            )),
            emptyList(),
        )
        val comments = PsiTreeUtil.findChildrenOfType(file, PsiComment::class.java)
        val holder = com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl(
            com.intellij.lang.annotation.AnnotationSession(file), false
        )
        comments.forEach { holder.runAnnotatorWithContext(it, JBangDirectiveAnnotator()) }
        val errors = holder.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }

        assertEquals("Errors: ${errors.map { it.message to file.text.substring(it.startOffset, it.endOffset) }}", 1, errors.size)
        assertEquals("jbang-mu", file.text.substring(errors.single().startOffset, errors.single().endOffset))
    }

    @Test
    fun testSourcesResolutionErrorHighlightsFailingPath() {
        val file = myFixture.configureByText("SourcesError.java", "//DEPS example:test:1\n//SOURCES missing.java\nclass SourcesError {}")
        JBangProjectService.getInstance(project).cacheResolved(
            file.virtualFile.path,
            ScriptInfo(sources = listOf(dev.jbang.idea.cli.SourceEntry(
                originalResource = "missing.java",
                error = "source is not resolvable",
            ))),
            emptyList(),
        )

        val comment = PsiTreeUtil.findChildrenOfType(file, PsiComment::class.java).single { it.text.startsWith("//SOURCES") }
        val holder = com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl(
            com.intellij.lang.annotation.AnnotationSession(file), false
        )
        holder.runAnnotatorWithContext(comment, JBangDirectiveAnnotator())

        assertTrue("Failing //SOURCES resource should be highlighted", holder.any {
            it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR &&
                it.message == "source is not resolvable" &&
                file.text.substring(it.startOffset, it.endOffset) == "missing.java"
        })
    }

    @Test
    fun testDependencyResolutionErrorHighlightsCoordinate() {
        val coordinate = "example:missing:99"
        val file = myFixture.configureByText("DepsError.java", "//DEPS $coordinate\nclass DepsError {}")
        val path = file.virtualFile.path
        JBangProjectService.getInstance(project).cacheResolved(
            path,
            ScriptInfo(dependencies = listOf(coordinate)),
            emptyList(),
        )
        assertNotNull("ScriptInfo should be cached", JBangProjectService.getInstance(project).getInfo(path))
        val comment = PsiTreeUtil.findChildrenOfType(file, PsiComment::class.java).single()
        val holder = com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl(
            com.intellij.lang.annotation.AnnotationSession(file), false
        )
        holder.runAnnotatorWithContext(comment, JBangDirectiveAnnotator())

        assertTrue("Should highlight unresolved dependency coordinate, got: ${holder.map { "${it.severity}:${it.message}" }}", holder.any {
            it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR &&
                it.message == "Unable to resolve dependency: $coordinate" &&
                file.text.substring(it.startOffset, it.endOffset) == coordinate
        })
    }

    @Test
    fun testUnknownDirectiveIsWarning() {
        val file = myFixture.addFileToProject("Unknown.java", "//WAT value\nclass Unknown {}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val warnings = safeHighlight().filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.WARNING }

        assertTrue(warnings.any { it.text == "//WAT" && it.description == "Unknown JBang directive: WAT" })
    }

    @Test
    fun testDirectiveWithoutArgumentsDoesNotCrashWhenInfoIsCached() {
        val file = myFixture.addFileToProject("EmptyDeps.java", "//DEPS\nclass EmptyDeps {}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        JBangProjectService.getInstance(project).cacheResolved(file.virtualFile.path, ScriptInfo(), emptyList())

        safeHighlight()
    }

    @Test
    fun testInvalidDepsGavIsError() {
        val file = myFixture.addFileToProject("InvalidDeps.java", "//DEPS com.google.guava\nclass InvalidDeps {}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val errors = safeHighlight().filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }

        assertTrue(errors.any { it.text == "com.google.guava" && it.description == "Invalid dependency; expected group:artifact:version" })
    }

    @Test
    fun testValidDepsFormsAreAccepted() {
        val file = myFixture.addFileToProject("ValidDeps.java", """
            //DEPS com.google.guava:guava:33.4.0-jre
            //DEPS /tmp/local.jar
            class ValidDeps {}
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val invalidGavErrors = safeHighlight().filter {
            it.description == "Invalid dependency; expected group:artifact:version"
        }

        assertTrue(invalidGavErrors.isEmpty())
    }

    @Test
    fun testDuplicateDepsIsWarning() {
        val file = myFixture.addFileToProject("DuplicateDeps.java", """
            //DEPS com.google.guava:guava:33.4.0-jre
            //DEPS com.google.guava:guava:33.4.0-jre
            class DuplicateDeps {}
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val warnings = safeHighlight().filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.WARNING }

        assertEquals(1, warnings.count { it.description == "Duplicate //DEPS: com.google.guava:guava:33.4.0-jre" })
    }

    @Test
    fun testRegularCommentNotAnnotated() {
        val file = myFixture.addFileToProject("RegularComment.java", """
            public class RegularComment {
                // regular comment
                /* block comment */
            }
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val highlights = safeHighlight()
        val annotatedTexts = highlights
            .filter { it.forcedTextAttributes != null }
            .map { it.text }

        assertFalse("Regular comment should not be highlighted as directive",
            annotatedTexts.any { it.startsWith("// regular") })
    }
}
