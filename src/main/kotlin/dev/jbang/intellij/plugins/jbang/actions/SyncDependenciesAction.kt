package dev.jbang.intellij.plugins.jbang.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import dev.jbang.intellij.plugins.jbang.isJbangScript
import dev.jbang.intellij.plugins.jbang.isJbangScriptFile
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.idea.util.projectStructure.getModuleDir
import org.jetbrains.plugins.gradle.util.GradleConstants


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
                var buildGradle = LocalFileSystem.getInstance().findFileByPath(project.basePath + "/build.gradle")
                if (module != null && buildGradle != null) {
                    val buildGradleOfModule = LocalFileSystem.getInstance().findFileByPath(module.getModuleDir() + "/build.gradle")
                    if (buildGradleOfModule != null) {
                        buildGradle = buildGradleOfModule;
                    }
                    //script file in main source set
                    var moduleName = module.name
                    if (moduleName.contains('.')) {
                        moduleName = moduleName.substring(moduleName.lastIndexOf('.') + 1)
                    }
                    val sourceSetName = if (project.name == moduleName) {
                        "main"
                    } else {
                        moduleName
                    }
                    val psiBuildGradle = buildGradle.toPsiFile(project)!!
                    val dependenciesFromGradle = findDependenciesFromGradle(psiBuildGradle.text, sourceSetName)
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
                            document.setText(addDependenciesToScript(jbangScriptFile.text, newDependenciesForScript))
                        }
                    }
                    if (newDependenciesForGradle.isNotEmpty()) {
                        ApplicationManager.getApplication().runWriteAction {
                            val documentManager = PsiDocumentManager.getInstance(project)
                            val document = documentManager.getDocument(psiBuildGradle)!!
                            document.setText(addDependenciesToGradle(psiBuildGradle.text, newDependenciesForGradle, sourceSetName))
                        }
                        refreshProject(project)
                    }
                    println("  ")
                }
            }
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
            lines.filter { it.startsWith(directive) }.map { it.substring(it.indexOf(" ")).trim() }.map { it.trim('\'').trim('"') }.toSet()
        } else {
            return emptySet()
        }
    }

    private fun findDependenciesFromScript(code: String): Set<String> {
        return code.lines().filter { it.startsWith("//DEPS ") }.map { it.substring(it.indexOf(" ")).trim() }.toSet()
    }

    private fun addDependenciesToScript(code: String, newDependencies: Set<String>): String {
        val lines = code.lines()
        val newLines = lines.toMutableList()
        val elements = newDependencies.map { "//DEPS $it" }
        val offset = lines.indexOfLast { it.startsWith("//DEPS ") }
        if (offset < 0) {
            if (lines[0].startsWith("///usr/bin/env jbang")) {  //append to jbang declare
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
            "\t$directive '${it}'"
        }
        //dependencies block found
        val dependenciesOffset = lines.indexOfFirst { it.trim().startsWith("dependencies ") }
        if (dependenciesOffset >= 0) {
            val offset = lines.indexOfLast { it.trim().startsWith(directive) }
            if (offset >= 0) { // append to implementation
                newLines.addAll(offset + 1, elements)
            } else {  //append to `dependencies {` block
                newLines.add(dependenciesOffset + 1, "\t//dependencies for $sourceSetName SourceSet")
                newLines.addAll(dependenciesOffset + 2, elements)
            }
        } else { // add new `dependencies {}` block
            newLines.add("dependencies {")
            newLines.add("\t//dependencies for $sourceSetName SourceSet")
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
}