package dev.jbang.idea.run

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
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
}
