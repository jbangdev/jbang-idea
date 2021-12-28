package dev.jbang.intellij.plugins.jbang.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import dev.jbang.intellij.plugins.jbang.JBANG_DECLARE
import dev.jbang.intellij.plugins.jbang.JBangCli.resolveScriptDependencies
import dev.jbang.intellij.plugins.jbang.getJBangCmdAbsolutionPath
import dev.jbang.intellij.plugins.jbang.isJbangScript
import dev.jbang.intellij.plugins.jbang.isJbangScriptFile
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.idea.util.projectStructure.getModuleDir
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.BufferedReader

/**
 * Sync dependencies between JBang script and Gradle dependencies
 *
 * @author linux_china
 */
class SyncDependenciesAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val jbangScriptFile = e.getData(CommonDataKeys.PSI_FILE)
        if (jbangScriptFile != null && isJbangScriptFile(jbangScriptFile.name)) {
            if (isJbangScript(jbangScriptFile.text)) {
                val project = e.getData(CommonDataKeys.PROJECT)!!
                val buildGradle = LocalFileSystem.getInstance().findFileByPath(project.basePath + "/build.gradle")
                if (buildGradle != null) {
                    e.presentation.text = "Sync DEPS Between JBang and Gradle"
                } else {
                    e.presentation.text = "Sync JBang DEPS to Module"
                }
                e.presentation.isEnabledAndVisible = true
                return
            }
        }
        e.presentation.isEnabledAndVisible = false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val jbangScriptFile = e.getData(CommonDataKeys.PSI_FILE)
        if (jbangScriptFile != null && isJbangScriptFile(jbangScriptFile.name)) {
            if (isJbangScript(jbangScriptFile.text)) {
                val project = e.getData(CommonDataKeys.PROJECT)!!
                val module = jbangScriptFile.module
                if (module != null) {
                    var buildGradle = LocalFileSystem.getInstance().findFileByPath(project.basePath + "/build.gradle")
                    val buildGradleOfModule = LocalFileSystem.getInstance().findFileByPath(module.getModuleDir() + "/build.gradle")
                    if (buildGradleOfModule != null) {
                        buildGradle = buildGradleOfModule;
                    }
                    if (buildGradle != null) { // sync dependencies between DEPS and gradle
                        syncDependenciesBetweenJBangAndGradle(project, module, buildGradle.toPsiFile(project)!!, jbangScriptFile)
                    } else { //sync DEPS to IDEA's module
                        //disable dependencies resolve by JBangBundle because of ClassLoader problem
                        //syncDepsToModule(module, jbangScriptFile)
                        syncDepsToModuleWithCmd(module, jbangScriptFile)
                        val javaVersion = jbangScriptFile.text.lines().firstOrNull { it.startsWith("//JAVA") }
                        if (javaVersion != null) {
                            syncJavaVersionToProject(project, module, javaVersion.substring(6).trim())
                        }
                    }
                }
            }
        }
    }

    private fun syncDependenciesBetweenJBangAndGradle(project: Project, module: Module, buildGradle: PsiFile, jbangScriptFile: PsiFile) {
        var moduleName = module.name
        if (moduleName.contains('.')) {
            moduleName = moduleName.substring(moduleName.lastIndexOf('.') + 1)
        }
        val sourceSetName = if (project.name == moduleName) {
            "main"
        } else {
            moduleName
        }
        val dependenciesFromGradle = findDependenciesFromGradle(buildGradle.text, sourceSetName)
        val dependenciesFromScript = findDependenciesFromScript(jbangScriptFile.text)
        val allDependencies = HashSet(dependenciesFromGradle).apply {
            addAll(dependenciesFromScript)
        }
        val newDependenciesForGradle = HashSet(allDependencies).apply {
            removeAll(dependenciesFromGradle)
        }
        val newDependenciesForScript = HashSet(allDependencies).apply {
            removeAll(dependenciesFromScript)
        }
        if (newDependenciesForScript.isNotEmpty()) {
            ApplicationManager.getApplication().runWriteAction {
                val documentManager = PsiDocumentManager.getInstance(project)
                val document = documentManager.getDocument(jbangScriptFile)!!
                document.setText(addDependenciesToScript(jbangScriptFile.name, jbangScriptFile.text, newDependenciesForScript))
            }
        }
        if (newDependenciesForGradle.isNotEmpty()) {
            ApplicationManager.getApplication().runWriteAction {
                val documentManager = PsiDocumentManager.getInstance(project)
                val document = documentManager.getDocument(buildGradle)!!
                document.setText(addDependenciesToGradle(buildGradle.text, newDependenciesForGradle, sourceSetName))
            }
            refreshProject(project)
        }
    }

    private fun findDependenciesFromGradle(code: String, sourceSetName: String): Set<String> {
        val lines = code.lines().map { it.trim() }
        val directive = if (sourceSetName == "main") {
            "implementation "
        } else {
            "${sourceSetName}Implementation "
        }
        val sourceSetFound = lines.any { it.startsWith(directive) }
        return if (sourceSetFound) {
            lines.asSequence().filter { it.startsWith(directive) }.map { it.substring(it.indexOf(" ")).trim() }.map { it.trim('\'').trim('"') }.map {
                if (it.startsWith("platform")) {
                    it.substring(8).trim().trim('(').trim(')').trim('\'').trim('"') + "@pom"
                } else {
                    it
                }
            }.toSet()
        } else {
            return emptySet()
        }
    }

    private fun findDependenciesFromScript(code: String): Set<String> {
        return code.lines().filter { it.startsWith("//DEPS ") }.map { it.substring(it.indexOf(" ")).trim() }.toSet()
    }

    private fun addDependenciesToScript(fileName: String, code: String, newDependencies: Set<String>): String {
        val lines = code.lines()
        val newLines = lines.toMutableList()
        val elements = newDependencies.map { "//DEPS $it" }
        val offset = lines.indexOfLast { it.startsWith("//DEPS ") }
        if (offset < 0) {
            if (lines[0].startsWith(JBANG_DECLARE)) {  //append to jbang declare
                newLines.addAll(1, elements)
            } else { // append to head of script
                newLines.addAll(0, elements)
            }
        } else { //append to //DEPS
            newLines.addAll(offset + 1, elements)
        }
        return newLines.joinToString("\n")
    }

    private fun addDependenciesToGradle(code: String, newDependencies: Set<String>, sourceSetName: String): String {
        val lines = code.lines()
        val newLines = lines.toMutableList()
        val directive = if (sourceSetName == "main") {
            "implementation "
        } else {
            "${sourceSetName}Implementation "
        }
        val elements = newDependencies.map {
            if (it.endsWith("@pom")) {
                "  $directive platform('${it.subSequence(0, it.length - 4)}')"
            } else {
                "  $directive '${it}'"
            }
        }
        //dependencies block found
        val dependenciesOffset = lines.indexOfFirst { it.trim().startsWith("dependencies ") }
        if (dependenciesOffset >= 0) {
            val offset = lines.indexOfLast { it.trim().startsWith(directive) }
            if (offset >= 0) { // append to implementation
                newLines.addAll(offset + 1, elements)
            } else {  //append to `dependencies {` block
                newLines.add(dependenciesOffset + 1, "  //dependencies for $sourceSetName SourceSet")
                newLines.addAll(dependenciesOffset + 2, elements)
            }
        } else { // add new `dependencies {}` block
            newLines.add("dependencies {")
            newLines.add("  //dependencies for $sourceSetName SourceSet")
            newLines.addAll(elements)
            newLines.add("}")
        }
        return newLines.joinToString("\n")
    }

    private fun refreshProject(project: Project) {
        FileDocumentManager.getInstance().saveAllDocuments()
        val projectSystemId = GradleConstants.SYSTEM_ID
        ExternalSystemUtil.refreshProject(
            project.basePath!!, ImportSpecBuilder(project, projectSystemId).withArguments("--refresh-dependencies")
        )
    }

    private fun syncDepsToModule(module: Module, jbangScriptFile: PsiFile) {
        val fullPath = jbangScriptFile.virtualFile.path
        val dependencies = resolveScriptDependencies(fullPath)
        ApplicationManager.getApplication().runWriteAction {
            try {
                val newDependencies = dependencies.filter { !it.contains(".jbang") }
                replaceJbangModuleLib(module, newDependencies)
            } catch (e: Exception) {
                val errorText = "Failed to resolve dependencies from " + jbangScriptFile.name + ", please check your //DEPS in your code"
                val jbangNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("JBang Failure");
                jbangNotificationGroup.createNotification("Failed to resolve DEPS", errorText, NotificationType.ERROR).notify(module.project)
            }
        }
    }

    private fun syncDepsToModuleWithCmd(module: Module, jbangScriptFile: PsiFile) {
        ApplicationManager.getApplication().runWriteAction {
            // get new dependencies from `jbang info classpath --quiet script_path`
            val fullPath = jbangScriptFile.virtualFile.path
            val jbangCmd = getJBangCmdAbsolutionPath()
            val pb = ProcessBuilder(jbangCmd, "info", "classpath", "--fresh", fullPath)
            val process = pb.start()
            process.waitFor()
            if (process.exitValue() != 0) {
                val errorText = process.errorStream.bufferedReader().use(BufferedReader::readText).trim()
                val jbangNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("JBang Failure");
                jbangNotificationGroup.createNotification("Failed to resolve DEPS", errorText, NotificationType.ERROR).notify(module.project)
            } else {
                val allText = process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
                val newDependencies = allText.split(':', ';').filter { !it.contains(".jbang") }
                replaceJbangModuleLib(module, newDependencies)
            }
        }
    }

    private fun syncJavaVersionToProject(project: Project, module: Module, version: String) {
        val projectRootManager = ProjectRootManager.getInstance(project)
        val projectSdk = projectRootManager.projectSdk
        if (projectSdk == null || projectSdk.name != version) {
            val javaSdk = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()).firstOrNull { it.name == version }
            if (javaSdk != null) {
                ApplicationManager.getApplication().runWriteAction {
                    projectRootManager.projectSdk = javaSdk
                    LanguageLevelProjectExtension.getInstance(project).default = true
                }
            }
        }
    }

    private fun replaceJbangModuleLib(module: Module, newDependencies: List<String>) {
        // remove jbang library
        val moduleRootManager = ModuleRootManager.getInstance(module)
        val modifiableModel = moduleRootManager.modifiableModel
        val jbangLib = modifiableModel.moduleLibraryTable.getLibraryByName("jbang");
        if (jbangLib != null) {
            modifiableModel.moduleLibraryTable.removeLibrary(jbangLib)
            modifiableModel.commit()
        }
        // add jbang dependencies
        ModuleRootModificationUtil.addModuleLibrary(module, "jbang", newDependencies.map { "jar://${it}!/" }.toList(), listOf())
        val jbangNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("JBang Success")
        jbangNotificationGroup.createNotification("Succeed to sync DEPS", "${newDependencies.size} jars synced!", NotificationType.INFORMATION).notify(module.project)
    }

}