package dev.jbang.idea.run

import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.jbang.idea.JBangFeatureTips
import dev.jbang.idea.project.JBangScriptDetector
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Test

/**
 * Tests run markers on realistic jbang scripts (without shebang to avoid
 * Java parse errors that poison the test logger).
 * Shebang-specific PSI behavior is tested in JBangScriptDetectorTest.
 */
class JBangRunLineMarkerRealFileTest : LightJavaCodeInsightFixtureTestCase() {

    // CLI template WITHOUT the shebang (which causes Java parse errors in tests)
    private val cliTemplateNoShebang = """
//DEPS info.picocli:picocli:4.6.3

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(name = "tako", mixinStandardHelpOptions = true, version = "tako 0.1",
        description = "tako made with jbang")
class tako implements Callable<Integer> {

    @Parameters(index = "0", description = "The greeting to print", defaultValue = "World!")
    private String greeting;

    public static void main(String... args) {
        int exitCode = new CommandLine(new tako()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("Hello " + greeting);
        return 0;
    }
}
    """.trimIndent()

    @Test
    fun testRunMarkerOnRealisticCliTemplate() {
        val psiFile = myFixture.configureByText("tako.java", cliTemplateNoShebang)
        val marker = JBangRunLineMarker()

        // //DEPS comment
        val comments = PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)
        val depsComment = comments.find { it.text.startsWith("//DEPS") }
        assertNotNull("Should find //DEPS", depsComment)
        assertNotNull("Marker on //DEPS", marker.getInfo(depsComment!!))

        // class identifier
        val clazz = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
            .find { it.name == "tako" }
        assertNotNull("Should find class tako", clazz)
        assertNotNull("Marker on class name", marker.getInfo(clazz!!.nameIdentifier!!))

        // main method identifier
        val mainMethod = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            .find { it.name == "main" }
        assertNotNull("Should find main", mainMethod)
        assertNotNull("Marker on main()", marker.getInfo(mainMethod!!.nameIdentifier!!))
    }

    @Test
    fun testNoMarkerOnNonJBangRealisticFile() {
        val psiFile = myFixture.configureByText("App.java", """
            import java.util.List;

            public class App {
                public static void main(String[] args) {
                    System.out.println("hello");
                }
            }
        """.trimIndent())

        val marker = JBangRunLineMarker()
        val mainMethod = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            .find { it.name == "main" }
        assertNotNull("Should find main", mainMethod)
        assertNull("No jbang marker on plain Java main()", marker.getInfo(mainMethod!!.nameIdentifier!!))
    }

    @Test
    fun testRunMarkerOnKotlinShebang() {
        val psiFile = myFixture.configureByText("Main.kt", """
            ///usr/bin/env jbang "${'$'}0" "${'$'}@" ; exit ${'$'}?

            public fun main() {
                println("Hello World")
            }
        """.trimIndent())

        assertNotNull(
            "Kotlin shebang should get a run marker",
            JBangRunLineMarker().getInfo(psiFile.findElementAt(0)!!),
        )
        myFixture.doHighlighting()
        assertTrue(
            "Kotlin registration should expose the JBang gutter icon",
            myFixture.findAllGutters().any { it.tooltipText?.contains("JBang") == true },
        )
        val gutter = myFixture.editor.gutter as EditorGutterComponentEx
        val markerBounds = gutter.getGutterRenderersAndRectangles(0)
            .first { it.first.tooltipText?.contains("JBang") == true }
            .second
        assertEquals(
            java.awt.Point(markerBounds.x + markerBounds.width / 2, markerBounds.y + markerBounds.height / 2),
            JBangFeatureTips.runMarkerPoint(gutter, 0),
        )
    }

    @Test
    fun testRunMarkerOnKotlinDepsComment() {
        val psiFile = myFixture.configureByText("hello.kt", """
            //DEPS com.google.guava:guava:33.0-jre
            fun main() { println("hello") }
        """.trimIndent())

        val marker = JBangRunLineMarker()
        val comments = PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)
        val depsComment = comments.find { it.text.startsWith("//DEPS") }
        assertNotNull("Should find //DEPS in Kotlin file", depsComment)
        assertNotNull("Kotlin //DEPS should get a run marker", marker.getInfo(depsComment!!))
    }

    @Test
    fun testRunMarkerOnGroovyDepsComment() {
        val psiFile = myFixture.configureByText("hello.groovy", """
            //DEPS com.google.guava:guava:33.0-jre
            println 'hello'
        """.trimIndent())

        val marker = JBangRunLineMarker()
        val comments = PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)
        val depsComment = comments.find { it.text.startsWith("//DEPS") }
        assertNotNull("Should find //DEPS in Groovy file", depsComment)
        assertNotNull("Groovy //DEPS should get a run marker", marker.getInfo(depsComment!!))
    }

    @Test
    fun testRunMarkerOnKotlinMainFunction() {
        val psiFile = myFixture.configureByText("hello.kt", """
            //DEPS com.google.guava:guava:33.0-jre
            fun main() { println("hello") }
        """.trimIndent())

        val marker = JBangRunLineMarker()
        val mainFun = PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java)
            .find { it.name == "main" }
        assertNotNull("Should find fun main() in Kotlin file", mainFun)
        assertNotNull(
            "Kotlin main function should get a run marker",
            marker.getInfo(mainFun!!.nameIdentifier!!),
        )
    }

    @Test
    fun testRunMarkerOnKotlinClass() {
        val psiFile = myFixture.configureByText("Hello.kt", """
            //DEPS com.google.guava:guava:33.0-jre
            class Hello {
                fun run() { println("hello") }
            }
        """.trimIndent())

        val marker = JBangRunLineMarker()
        val clazz = PsiTreeUtil.findChildrenOfType(psiFile, KtClass::class.java)
            .find { it.name == "Hello" }
        assertNotNull("Should find class Hello in Kotlin file", clazz)
        assertNotNull(
            "Kotlin class name should get a run marker",
            marker.getInfo(clazz!!.nameIdentifier!!),
        )
    }

    @Test
    fun testScriptDetectorRecognizesKotlinRoot() {
        val file = myFixture.addFileToProject("hello.kt", "//DEPS com.google.guava:guava:33.0-jre\nfun main() {}")
        assertTrue("Kotlin file with //DEPS should be a root", JBangScriptDetector.isRootScript(file.virtualFile))
    }

    @Test
    fun testScriptDetectorRecognizesGroovyRoot() {
        val file = myFixture.addFileToProject("hello.groovy", "//DEPS com.google.guava:guava:33.0-jre\nprintln 'hello'")
        assertTrue("Groovy file with //DEPS should be a root", JBangScriptDetector.isRootScript(file.virtualFile))
    }

    @Test
    fun testKDocRunMarkerAttachesToFirstLeaf() {
        // The light fixture parses `///` as PsiCommentImpl, unlike the live KDoc path.
        // The existing shebang gutter test covers rendering; this catches its KDoc target.
        val psiFile = myFixture.configureByText("hello.kt", """
            /** Documentation. */
            fun main() {}
        """.trimIndent())
        val kdoc = PsiTreeUtil.findChildOfType(
            psiFile,
            org.jetbrains.kotlin.kdoc.psi.api.KDoc::class.java,
        )!!
        val marker = JBangRunLineMarker()

        assertNull("Non-leaf KDoc must not receive a marker", marker.commentForMarker(kdoc))
        assertSame(
            "The first KDoc leaf must attach its marker to the whole KDoc",
            kdoc,
            marker.commentForMarker(kdoc.firstChild),
        )
    }
}
