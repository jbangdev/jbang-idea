package dev.jbang.idea.run

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class JBangJavaHighlightingTest : LightJavaCodeInsightFixtureTestCase() {

    fun testUnresolvedMethodIsHighlightedInNormalJavaModule() {
        val file = myFixture.addFileToProject("Main.java", """
            public class Main {
                public static void main(String[] args) {
                    System.out.println_("Hello");
                }
            }
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val errors = myFixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }

        assertTrue("Unresolved method must retain Java error highlighting", errors.any {
            it.text == "println_" && it.description?.contains("Cannot resolve method") == true
        })
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
