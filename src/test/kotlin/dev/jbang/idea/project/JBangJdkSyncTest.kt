package dev.jbang.idea.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.jbang.idea.cli.ScriptInfo
import java.nio.file.Files
import java.nio.file.Path

class JBangJdkSyncTest : LightJavaCodeInsightFixtureTestCase() {

    override fun tearDown() {
        JBangProjectService.getInstance(project).clear()
        super.tearDown()
    }

    fun testActiveRootSetsStandaloneProjectSdk() {
        val roots = ProjectRootManager.getInstance(project)
        val previous = roots.projectSdk
        val info = ScriptInfo(requestedJavaVersion = "active-test", availableJdkPath = System.getProperty("java.home"))
        try {
            val service = JBangProjectService.getInstance(project)
            service.cacheResolved("/tmp/Root.java", info, emptyList())

            service.setActiveRoot("/tmp/Root.java")

            assertEquals("JBang active-test", roots.projectSdk?.name)
        } finally {
            ApplicationManager.getApplication().runWriteAction { roots.projectSdk = previous }
            ProjectJdkTable.getInstance().findJdk("JBang active-test")?.let { sdk ->
                ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().removeJdk(sdk) }
            }
        }
    }

    fun testResyncChangesSdkWhenActiveRootsJavaDirectiveChanges() {
        val roots = ProjectRootManager.getInstance(project)
        val previous = roots.projectSdk
        val service = JBangProjectService.getInstance(project)
        try {
            service.cacheResolved(
                "/tmp/Root.java",
                ScriptInfo(requestedJavaVersion = "before-test", availableJdkPath = System.getProperty("java.home")),
                emptyList(),
            )
            service.setActiveRoot("/tmp/Root.java")

            service.cacheResolved(
                "/tmp/Root.java",
                ScriptInfo(requestedJavaVersion = "after-test", availableJdkPath = System.getProperty("java.home")),
                emptyList(),
            )

            assertEquals("JBang after-test", roots.projectSdk?.name)
        } finally {
            ApplicationManager.getApplication().runWriteAction { roots.projectSdk = previous }
            listOf("JBang before-test", "JBang after-test").forEach { name ->
                ProjectJdkTable.getInstance().findJdk(name)?.let { sdk ->
                    ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().removeJdk(sdk) }
                }
            }
        }
    }

    fun testActiveRootDoesNotReplaceSdkInGradleProject() {
        val buildFile = Path.of(project.basePath!!, "build.gradle")
        Files.createDirectories(buildFile.parent)
        Files.writeString(buildFile, "plugins { id 'java' }")
        val roots = ProjectRootManager.getInstance(project)
        val previous = roots.projectSdk
        val info = ScriptInfo(requestedJavaVersion = "mixed-test", availableJdkPath = System.getProperty("java.home"))
        try {
            val service = JBangProjectService.getInstance(project)
            service.cacheResolved("/tmp/Tool.java", info, emptyList())

            service.setActiveRoot("/tmp/Tool.java")

            assertSame(previous, roots.projectSdk)
        } finally {
            Files.deleteIfExists(buildFile)
            ProjectJdkTable.getInstance().findJdk("JBang mixed-test")?.let { sdk ->
                ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().removeJdk(sdk) }
            }
        }
    }

    fun testRegistersJdkReportedByJBang() {
        val name = "JBang test-current"
        val table = ProjectJdkTable.getInstance()
        table.findJdk(name)?.let { sdk ->
            ApplicationManager.getApplication().runWriteAction { table.removeJdk(sdk) }
        }
        try {
            JBangJdkSync.register(
                ScriptInfo(requestedJavaVersion = "test-current", availableJdkPath = System.getProperty("java.home"))
            )

            assertNotNull(table.findJdk(name))
        } finally {
            table.findJdk(name)?.let { sdk ->
                ApplicationManager.getApplication().runWriteAction { table.removeJdk(sdk) }
            }
        }
    }
}
