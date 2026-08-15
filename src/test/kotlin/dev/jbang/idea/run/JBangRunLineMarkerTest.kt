package dev.jbang.idea.run

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.jbang.idea.JBangPlugin
import org.junit.Test

class JBangRunLineMarkerTest : LightJavaCodeInsightFixtureTestCase() {

    @Test
    fun testShebangMarkerIsRegisteredOnLeafOnly() {
        val file = myFixture.configureByText("Shebang.java", "///usr/bin/env jbang\nclass Shebang {}")
        val comment = PsiTreeUtil.findChildOfType(file, PsiDocComment::class.java)!!
        val marker = JBangRunLineMarker()

        assertNull("Non-leaf doc comment must not receive a marker", marker.getInfo(comment))
        assertNotNull("First leaf token should receive the shebang marker", marker.getInfo(comment.firstChild))
    }

    @Test
    fun testRunLineMarkerOnDepsComment() {
        val psiFile = myFixture.configureByText("WithDeps.java", """
            //DEPS com.google.guava:guava:33.0-jre
            public class WithDeps {
                public static void main(String[] args) {}
            }
        """.trimIndent())

        val marker = JBangRunLineMarker()
        val comments = PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)
        val depsComment = comments.find { it.text.startsWith("//DEPS") }
        assertNotNull("Should find //DEPS comment", depsComment)

        val info = marker.getInfo(depsComment!!)
        assertNotNull("Should produce a run marker for //DEPS", info)
    }

    @Test
    fun testRunMarkerOnMainMethod() {
        val psiFile = myFixture.configureByText("WithMain.java", """
            //DEPS com.google.guava:guava:33.0-jre
            public class WithMain {
                public static void main(String[] args) {}
            }
        """.trimIndent())

        val marker = JBangRunLineMarker()
        // Find the "main" identifier inside the main method
        val mainMethod = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            .find { it.name == "main" }
        assertNotNull("Should find main method", mainMethod)

        val mainIdentifier = mainMethod!!.nameIdentifier
        assertNotNull("main method should have name identifier", mainIdentifier)

        val info = marker.getInfo(mainIdentifier!!)
        assertNotNull("Should produce a run marker on main() in a jbang script", info)
    }

    @Test
    fun testRunMarkerOnClassIdentifier() {
        val psiFile = myFixture.configureByText("NoMain.java", """
            //DEPS com.google.guava:guava:33.0-jre
            public class NoMain {
                public void doStuff() {}
            }
        """.trimIndent())

        val marker = JBangRunLineMarker()
        val clazz = PsiTreeUtil.findChildOfType(psiFile, PsiClass::class.java)
        assertNotNull("Should find class", clazz)

        val classIdentifier = clazz!!.nameIdentifier
        assertNotNull("Class should have name identifier", classIdentifier)

        val info = marker.getInfo(classIdentifier!!)
        assertNotNull("Should produce a run marker on the class name in a jbang script", info)
    }

    @Test
    fun testNoRunMarkerOnMainInPlainJava() {
        val psiFile = myFixture.configureByText("PlainMain.java", """
            public class PlainMain {
                public static void main(String[] args) {}
            }
        """.trimIndent())

        val marker = JBangRunLineMarker()
        val mainMethod = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            .find { it.name == "main" }
        assertNotNull("Should find main method", mainMethod)

        val info = marker.getInfo(mainMethod!!.nameIdentifier!!)
        assertNull("Plain Java main should NOT get a jbang run marker", info)
    }

    @Test
    fun testRunMarkerWorksOutsideSourceRoot() {
        // jbang scripts can live outside IntelliJ's module source roots.
        // The marker should still fire based on file content, not source root membership.
        val psiFile = myFixture.configureByText("standalone.java", """
            ///usr/bin/env jbang "${'$'}0" "${'$'}@" ; exit ${'$'}?
            //DEPS info.picocli:picocli:4.7.5
            public class standalone {
                public static void main(String[] args) {}
            }
        """.trimIndent())

        val marker = JBangRunLineMarker()

        // Should get marker on main() identifier
        val mainMethod = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            .find { it.name == "main" }
        assertNotNull("Should find main", mainMethod)
        val mainInfo = marker.getInfo(mainMethod!!.nameIdentifier!!)
        assertNotNull("main() should get jbang run marker even outside source root", mainInfo)

        // Should get marker on class identifier
        val clazz = PsiTreeUtil.findChildOfType(psiFile, PsiClass::class.java)
        assertNotNull("Should find class", clazz)
        val classInfo = marker.getInfo(clazz!!.nameIdentifier!!)
        assertNotNull("Class should get jbang run marker even outside source root", classInfo)
    }

    @Test
    fun testRunMarkerTooltipIncludesFileName() {
        val psiFile = myFixture.configureByText("tako.java", """
            //DEPS info.picocli:picocli:4.7.5
            public class tako {
                public static void main(String[] args) {}
            }
        """.trimIndent())

        val marker = JBangRunLineMarker()
        val comments = PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)
        val depsComment = comments.find { it.text.startsWith("//DEPS") }!!

        val info = marker.getInfo(depsComment)!!
        val tooltip = info.tooltipProvider.apply(depsComment)
        assertTrue("Tooltip should mention file name, got: $tooltip",
            tooltip.contains("tako.java"))
        assertTrue("Tooltip should mention JBang, got: $tooltip",
            tooltip.contains("JBang"))
    }

    @Test
    fun testNoRunMarkerOnRegularComment() {
        val psiFile = myFixture.configureByText("NoJBang.java", """
            // just a comment
            public class NoJBang {
                public static void main(String[] args) {}
            }
        """.trimIndent())

        val marker = JBangRunLineMarker()
        val comments = PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)
        for (comment in comments) {
            val info = marker.getInfo(comment)
            assertNull("Regular comments should not get a run marker", info)
        }
    }

    @Test
    fun testOnlyFirstDirectiveGetsMarker() {
        val psiFile = myFixture.configureByText("Multi.java", """
            //DEPS com.google.guava:guava:33.0-jre
            //JAVA 21
            public class Multi {
                public static void main(String[] args) {}
            }
        """.trimIndent())

        val marker = JBangRunLineMarker()
        val comments = PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)
        val depsComment = comments.find { it.text.startsWith("//DEPS") }
        val javaComment = comments.find { it.text.startsWith("//JAVA") }

        assertNotNull("First directive (//DEPS) should get marker", marker.getInfo(depsComment!!))
        assertNull("Second directive (//JAVA) should NOT get marker", marker.getInfo(javaComment!!))
    }
}
