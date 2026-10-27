package dev.jbang.idea.run

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class JBangJavaHighlightingTest : LightJavaCodeInsightFixtureTestCase() {

    fun testNormalJavaRetainsErrorHighlightingAndHasNoJBangGutter() {
        // Regression for #184: the Java-registered run line marker must not suppress the
        // highlighting daemon (it previously threw NoClassDefFoundError reaching Kotlin PSI),
        // and plain Java must not get a JBang gutter icon. (The light fixture has no JDK, so
        // an unresolved reference surfaces as a "Cannot resolve" error — enough to prove the
        // daemon still runs.)
        val file = myFixture.addFileToProject("Main.java", """
            public class Main {
                public static void main(String[] args) {
                    thisReferenceDoesNotExist();
                }
            }
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val errors = myFixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }

        assertTrue(
            "Normal Java must retain error highlighting; got: ${errors.map { it.description }}",
            errors.any { it.description?.contains("Cannot resolve") == true },
        )
        assertFalse("Normal Java must not have JBang gutter icons",
            myFixture.findAllGutters().any { it.tooltipText?.contains("JBang") == true })
    }

    fun testJavaLineMarkerWorksWithoutOptionalKotlinClasses() {
        val file = myFixture.configureByText("Main.java", """
            public class Main {
                public static void main(String[] args) {
                    System.out.println_("Hello");
                }
            }
        """.trimIndent())
        val markerClass = JBangRunLineMarker::class.java
        val loader = object : ClassLoader(markerClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name.startsWith("org.jetbrains.kotlin.")) throw ClassNotFoundException(name)
                if (name != markerClass.name && !name.startsWith(markerClass.name + "$")) {
                    return super.loadClass(name, resolve)
                }
                val loaded = findLoadedClass(name) ?: run {
                    val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.use { it.readBytes() }
                    defineClass(name, bytes, 0, bytes.size)
                }
                if (resolve) resolveClass(loaded)
                return loaded
            }
        }
        val marker = loader.loadClass(markerClass.name).getDeclaredConstructor().newInstance()
            as RunLineMarkerContributor

        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                assertNull("Normal Java must not get a JBang marker", marker.getInfo(element))
                super.visitElement(element)
            }
        })
    }
}
