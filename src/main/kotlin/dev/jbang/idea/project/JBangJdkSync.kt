package dev.jbang.idea.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import dev.jbang.idea.cli.ScriptInfo
import java.io.File

object JBangJdkSync {
    fun register(info: ScriptInfo): Sdk? {
        val (name, home) = sdkDetails(info) ?: return null
        val table = ProjectJdkTable.getInstance()
        table.findJdk(name)?.let { return it }
        val app = ApplicationManager.getApplication()
        val register = Runnable { app.runWriteAction { registerNow(name, home) } }
        when {
            app.isUnitTestMode -> register.run()
            app.isDispatchThread -> app.invokeLater(register, ModalityState.nonModal())
            else -> app.invokeAndWait(register, ModalityState.nonModal())
        }
        return table.findJdk(name)
    }

    fun applyToStandaloneProject(project: Project, info: ScriptInfo) {
        val base = project.basePath?.let(::File) ?: return
        if (listOf("pom.xml", "build.gradle", "build.gradle.kts").any { File(base, it).exists() }) return
        val (name, home) = sdkDetails(info) ?: return
        val app = ApplicationManager.getApplication()
        val apply = Runnable {
            app.runWriteAction {
                val sdk = registerNow(name, home)
                val roots = ProjectRootManager.getInstance(project)
                if (!project.isDisposed && roots.projectSdk != sdk) roots.projectSdk = sdk
            }
        }
        if (app.isUnitTestMode) apply.run()
        else app.invokeLater(apply, ModalityState.nonModal())
    }

    private fun registerNow(name: String, home: String): Sdk {
        val table = ProjectJdkTable.getInstance()
        return table.findJdk(name) ?: JavaSdk.getInstance().createJdk(name, home, false).also(table::addJdk)
    }

    private fun sdkDetails(info: ScriptInfo): Pair<String, String>? {
        val home = info.availableJdkPath
            ?.takeIf { File(it, "bin/java").exists() || File(it, "bin/java.exe").exists() }
            ?: return null
        val version = info.requestedJavaVersion ?: info.javaVersion ?: File(home).name
        return "JBang $version" to home
    }
}
