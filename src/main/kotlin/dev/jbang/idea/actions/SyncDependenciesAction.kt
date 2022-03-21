package dev.jbang.idea.actions

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
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import dev.jbang.idea.*
import dev.jbang.idea.JBangCli.resolveScriptDependencies
import dev.jbang.idea.JBangCli.resolveScriptInfo
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.idea.util.projectStructure.getModuleDir
import org.jetbrains.kotlin.idea.util.projectStructure.version
import org.jetbrains.plugins.gradle.util.GradleConstants


/**
 * Sync dependencies between JBang script and Gradle dependencies
 *
 * @author linux_china
 */
class SyncDependenciesAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val jbangScriptFile = e.getData(CommonDataKeys.PSI_FILE)
        if (jbangScriptFile != null && isJBangScriptFile(jbangScriptFile.name)) {
            if (isJBangScript(jbangScriptFile.text)) {
                val project = e.getData(CommonDataKeys.PROJECT)!!
                val buildGradle = LocalFileSystem.getInstance().findFileByPath(project.basePath + "/build.gradle")
                if (buildGradle != null) {
                    // check 
                    if (buildGradle.timeStamp <= jbangScriptFile.virtualFile.timeStamp) {
                        e.presentation.text = "Sync //DEPS to Gradle"
                    } else {
                        e.presentation.text = "Sync Gradle to //DEPS"
                    }
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
        if (jbangScriptFile != null && isJBangScriptFile(jbangScriptFile.name)) {
            if (isJBangScript(jbangScriptFile.text)) {
                val project = e.getData(CommonDataKeys.PROJECT)!!
                val module = jbangScriptFile.module
                if (module != null) {
                    var buildGradle = LocalFileSystem.getInstance().findFileByPath(project.basePath + "/build.gradle")
                    var moduleBuildGradle = false
                    val buildGradleOfModule = LocalFileSystem.getInstance().findFileByPath(module.getModuleDir() + "/build.gradle")
                    if (buildGradleOfModule != null) {
                        buildGradle = buildGradleOfModule
                        moduleBuildGradle = true
                    }
                    if (buildGradle != null) { // sync dependencies between DEPS and gradle
                        syncDependenciesBetweenJBangAndGradle(project, module, buildGradle.toPsiFile(project)!!, jbangScriptFile, moduleBuildGradle)
                    } else { //sync DEPS to IDEA's module
                        syncDepsToModule(module, jbangScriptFile)
                    }
                }
            }
        }
    }

    private fun syncDependenciesBetweenJBangAndGradle(project: Project, module: Module, buildGradle: PsiFile, jbangScriptFile: PsiFile, moduleBuildGradle: Boolean) {
        var moduleName = module.name
        if (moduleName.contains('.')) {
            moduleName = moduleName.substring(moduleName.lastIndexOf('.') + 1)
        }
        val sourceSetName = if (moduleBuildGradle || project.name == moduleName) {
            "main"
        } else {
            moduleName
        }
        val dependenciesFromGradle = findDependenciesFromGradle(buildGradle.text, sourceSetName)
        //findDependenciesFromModule(project, module);
        // Resolve dependency from `jbang info tools --quiet Hello.java` and GradleManager
        var dependenciesFromScript: List<String>?
        try {
            val scriptInfo = resolveScriptInfo(jbangScriptFile.virtualFile.path)
            dependenciesFromScript = scriptInfo.dependencies ?: listOf()
        } catch (e: Exception) {
            val errorText = "Failed to resolve info by `jbang info tools ${jbangScriptFile.virtualFile.path}`, and stacktrace: ${e.message}"
            val jbangNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_FAILURE)
            jbangNotificationGroup.createNotification("Failed to resolve DEPS", errorText, NotificationType.ERROR).notify(module.project)
            return
        }
        //add default dependency for Groovy Script
        if (jbangScriptFile.name.endsWith(".groovy")) {
            dependenciesFromScript = fillDefaultDependencyForGroovy(jbangScriptFile, dependenciesFromScript)
        }
        //check last modified timestamp
        val scriptIsNew = buildGradle.virtualFile.timeStamp <= jbangScriptFile.virtualFile.timeStamp
        if (scriptIsNew) { // sync //DEPS to build.gradle
            if (dependenciesFromScript.isNotEmpty()) {
                ApplicationManager.getApplication().runWriteAction {
                    val documentManager = PsiDocumentManager.getInstance(project)
                    val document = documentManager.getDocument(buildGradle)!!
                    var buildGradleContent = addDependenciesToGradle(buildGradle.text, dependenciesFromScript, sourceSetName)
                    if (!buildGradleContent.contains("\nsourceCompatibility")) {
                        val javaVersion = findJavaVersionFromScript(jbangScriptFile.text)
                        buildGradleContent = syncJavaVersionToGradle(buildGradleContent, javaVersion)
                    }
                    document.setText(buildGradleContent)
                }
                val jbangNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_SUCCESS)
                jbangNotificationGroup.createNotification(
                    "Succeed to sync //DEPS to Gradle",
                    "${dependenciesFromScript.size} dependencies synced!", NotificationType.INFORMATION
                ).notify(module.project)
                refreshProject(project)
            }
        } else {
            if (dependenciesFromGradle.isNotEmpty()) {
                ApplicationManager.getApplication().runWriteAction {
                    val documentManager = PsiDocumentManager.getInstance(project)
                    val document = documentManager.getDocument(jbangScriptFile)!!
                    document.setText(addDependenciesToScript(jbangScriptFile.name, jbangScriptFile.text, dependenciesFromGradle))
                    val jbangNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_SUCCESS)
                    jbangNotificationGroup.createNotification(
                        "Succeed to sync Gradle to //DEPS",
                        "${dependenciesFromGradle.size} dependencies synced!",
                        NotificationType.INFORMATION
                    ).notify(module.project)
                }
            }
        }
    }

    private fun fillDefaultDependencyForGroovy(jbangScriptFile: PsiFile, dependencies: List<String>): List<String> {
        val groovyDepsFound = dependencies.any { it.startsWith("org.codehaus.groovy") || it.startsWith("org.apache.groovy") }
        if (!groovyDepsFound) {
            var groovyVersion = jbangScriptFile.text.lines().firstOrNull { it.startsWith("//GROOVY ") }
            groovyVersion = groovyVersion?.substring(groovyVersion.indexOf(' ') + 1)?.trim() ?: "3.0.9"
            val groovyDeps = if (groovyVersion.startsWith("4.")) {
                "org.apache.groovy:groovy:${groovyVersion}"
            } else {
                "org.codehaus.groovy:groovy:${groovyVersion}"
            }
            return if (dependencies.isEmpty()) {
                arrayListOf(groovyDeps)
            } else {
                dependencies.toMutableList().apply {
                    add(groovyDeps)
                }
            }
        }
        return dependencies
    }

    private fun findDependenciesFromGradle(code: String, sourceSetName: String): List<String> {
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
            }.toList()
        } else {
            return emptyList()
        }
    }

    private fun addDependenciesToScript(@Suppress("UNUSED_PARAMETER") fileName: String, code: String, dependencies: List<String>): String {
        val lines = code.lines()
        val elements = dependencies.map { "//DEPS $it" }
        val offset = lines.indexOfLast { it.startsWith("//DEPS ") }
        if (offset < 0) {
            val newLines = lines.toMutableList()
            if (lines[0].startsWith(JBANG_DECLARE)) {  //append to jbang declare
                newLines.addAll(1, elements)
            } else { // append to head of script
                newLines.addAll(0, elements)
            }
            return newLines.joinToString("\n")
        } else { //append to last //DEPS
            val newLines = mutableListOf<String>()
            lines.subList(0, offset).filter { !it.startsWith("//DEPS ") }.forEach {
                newLines.add(it)
            }
            newLines.addAll(elements)
            newLines.addAll(lines.subList(offset + 1, lines.size))
            return newLines.joinToString("\n")
        }
    }

    private fun addDependenciesToGradle(code: String, dependencies: List<String>, sourceSetName: String): String {
        val lines = code.lines()
        val directive = if (sourceSetName == "main") {
            "implementation "
        } else {
            "${sourceSetName}Implementation "
        }
        val elements = dependencies.map {
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
            return if (offset >= 0) { // implementation found for source set
                val newLines = mutableListOf<String>()
                lines.subList(0, offset).filter { !it.trim().startsWith(directive) }.forEach {
                    newLines.add(it)
                }
                newLines.addAll(elements)
                newLines.addAll(lines.subList(offset + 1, lines.size))
                newLines.joinToString("\n")
            } else {  //no dependencies found for source set, and append to `dependencies {` block
                val newLines = lines.toMutableList()
                newLines.addAll(dependenciesOffset + 1, elements)
                newLines.joinToString("\n")
            }
        } else { // add new `dependencies {}` block
            val newLines = lines.toMutableList()
            newLines.add("dependencies {")
            newLines.addAll(elements)
            newLines.add("}")
            return newLines.joinToString("\n")
        }

    }


    private fun refreshProject(project: Project) {
        FileDocumentManager.getInstance().saveAllDocuments()
        val projectSystemId = GradleConstants.SYSTEM_ID
        ExternalSystemUtil.refreshProject(
            project.basePath!!, ImportSpecBuilder(project, projectSystemId).withArguments("--refresh-dependencies")
        )
    }

    private fun syncDepsToModule(module: Module, jbangScriptFile: PsiFile) {
        //save all documents first when to call JBang CLI
        ApplicationManager.getApplication().runWriteAction {
            FileDocumentManager.getInstance().saveAllDocuments()
        }
        val fullPath = jbangScriptFile.virtualFile.path
        ProgressManager.getInstance().run(object : Task.Backgroundable(module.project, "Syncing JBang dependencies and Java version") {
            var newDependencies: List<String>? = null
            override fun run(progressIndicator: ProgressIndicator) {
                try {
                    newDependencies = resolveScriptDependencies(fullPath)
                } catch (e: Exception) {
                    val errorText = "Failed to resolve dependencies from " + jbangScriptFile.name + ", please check your //DEPS in your code. Stacktrace: ${e.message}"
                    val jbangNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_FAILURE)
                    jbangNotificationGroup.createNotification("Failed to resolve DEPS", errorText, NotificationType.ERROR).notify(module.project)
                }
            }

            override fun onSuccess() {
                if (newDependencies != null) {
                    val dependencies = newDependencies!!
                    ApplicationManager.getApplication().runWriteAction {
                        replaceJBangModuleLib(module, jbangScriptFile.name, dependencies)
                        syncJavaVersionAndNotifyUser(dependencies)
                    }
                }
            }

            private fun syncJavaVersionAndNotifyUser(dependencies: List<String>) {
                val javaVersion = findJavaVersionFromScript(jbangScriptFile.text)
                val jdkChange = syncJavaVersionToModule(module, javaVersion)
                val jbangNotificationGroup = NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP_SUCCESS)
                var content = "${dependencies.size} jars synced"
                content = if (jdkChange) {
                    content.plus(", module's (${module.name}) Java version set to $javaVersion.")
                } else {
                    content.plus(".");
                }
                jbangNotificationGroup.createNotification("Succeed to sync DEPS", content, NotificationType.INFORMATION)
                    .notify(project)
            }
        })
    }

    private fun syncJavaVersionToGradle(buildGradleContent: String, javaVersion: String): String {
        val lines = buildGradleContent.lines()
        val javaVersionFound = lines.any { it.startsWith("sourceCompatibility") }
        return if (!javaVersionFound) {
            val newLines = lines.toMutableList()
            newLines.add("sourceCompatibility = \"${javaVersion}\"")
            newLines.add("targetCompatibility = \"${javaVersion}\"")
            newLines.joinToString("\n")
        } else {
            buildGradleContent
        }
    }

    private fun syncJavaVersionToModule(module: Module, version: String): Boolean {
        val moduleRootManager = ModuleRootManager.getInstance(module)
        val moduleSdk = moduleRootManager.sdk
        if (moduleSdk == null || moduleSdk.name != version) {
            val javaSdk = checkIfSdkExistsIfNotSyncWithJbang(version, module)
            if (javaSdk != null) {
                val languageLevel = LanguageLevel.valueOf(javaSdk.version?.name!!)
                ApplicationManager.getApplication().runWriteAction {
                    val modifiableRootModel = moduleRootManager.modifiableModel
                    modifiableRootModel.sdk = javaSdk
                    modifiableRootModel.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = languageLevel
                    modifiableRootModel.commit()
                }
                return true
            }
        }
        return false
    }

    private fun checkIfSdkExistsIfNotSyncWithJbang(version: String, module: Module): Sdk? {
        var javaSdk = getFirstSdkThatMatchesVersion(version)
        if (javaSdk == null) {
            val jbangJdkPath = JBangJdkService.getJbangJdkPath(version)
            if (jbangJdkPath != null) {
                //If IDEA does not have the requested JDK then just sync with JBang
                JBangJdkService.syncJdkWithIdeaWithProject(jbangJdkPath, version, module.project)
                javaSdk = getFirstSdkThatMatchesVersion(version)
            }
        }
        return javaSdk
    }

    private fun getFirstSdkThatMatchesVersion(version: String) =
        ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()).firstOrNull { it.name == version }

    private fun replaceJBangModuleLib(module: Module, scriptName: String, newDependencies: List<String>) {
        val libName = "${module.name}-jbang"

        // remove jbang library
        val moduleRootManager = ModuleRootManager.getInstance(module)
        val modifiableModel = moduleRootManager.modifiableModel
        val jbangLib = modifiableModel.moduleLibraryTable.getLibraryByName(libName)
        if (jbangLib != null) {
            modifiableModel.moduleLibraryTable.removeLibrary(jbangLib)
            modifiableModel.commit()
        }
        // add jbang dependencies
        if (newDependencies.isNotEmpty()) {
            ModuleRootModificationUtil.addModuleLibrary(module, libName, newDependencies.map { "jar://${it}!/" }.toList(), listOf())
        }
    }

    private fun findJavaVersionFromScript(scriptText: String): String {
        val javaVersion = scriptText.lines().firstOrNull { it.startsWith("//JAVA ") }
        return javaVersion?.substring(6)?.trim() ?: "11"
    }
}