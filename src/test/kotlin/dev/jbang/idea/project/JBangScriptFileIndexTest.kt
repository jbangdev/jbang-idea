package dev.jbang.idea.project

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test

class JBangScriptFileIndexTest : LightJavaCodeInsightFixtureTestCase() {

    @Test
    fun testCompilerOutputScriptsAreNotDiscoveredAsProjectRoots() {
        val source = myFixture.addFileToProject(
            "src/Root.java",
            "//DEPS example:source:1\nclass Root {}",
        ).virtualFile
        val generated = myFixture.addFileToProject(
            "build/classes/Root.java",
            "//DEPS example:generated:1\nclass Root {}",
        ).virtualFile

        ModuleRootModificationUtil.updateModel(module) { model ->
            model.getModuleExtension(CompilerModuleExtension::class.java).apply {
                inheritCompilerOutputPath(false)
                setCompilerOutputPath(generated.parent)
                setExcludeOutput(true)
            }
        }
        DumbService.getInstance(project).waitForSmartMode()

        val roots = JBangScriptFileIndex.findRootScripts(project)

        assertTrue(source in roots)
        assertFalse("Compiler output must not become a JBang root", generated in roots)
    }
}
