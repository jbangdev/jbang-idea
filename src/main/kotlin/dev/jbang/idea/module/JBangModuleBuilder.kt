package dev.jbang.idea.module

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleBuilderListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import dev.jbang.idea.JBangCli.generateScriptFrommTemplate
import dev.jbang.idea.jbangIcon
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.util.projectStructure.sdk
import java.util.*
import java.util.regex.Pattern
import javax.swing.Icon
import kotlin.io.path.absolutePathString


class JBangModuleBuilder : JavaModuleBuilder(), ModuleBuilderListener {

    init {
        addListener(this)
    }

    override fun getBuilderId(): String {
        return "JBang"
    }

    override fun getNodeIcon(): Icon {
        return jbangIcon
    }

    override fun getPresentableName(): String {
        return "JBang"
    }

    override fun moduleCreated(module: Module) {
        val moduleRootManager = ModuleRootManager.getInstance(module)
        val properties = Properties()
        properties.putAll(FileTemplateManager.getInstance(module.project).defaultProperties)
        val javaSdkVersion = module.sdk?.versionString
        val javaVersion = if (javaSdkVersion != null) {
            (19 downTo 9).firstOrNull { javaSdkVersion.contains("${it}.0") } ?: 8
        } else {
            11
        }
        properties["JAVA_VERSION"] = javaVersion
        val groovyJarFile = moduleRootManager.orderEntries().allLibrariesAndSdkClassesRoots.firstOrNull() {
            it.name.startsWith("groovy") && it.name.endsWith(".jar")
        }
        val kotlinJarFile = moduleRootManager.orderEntries().allLibrariesAndSdkClassesRoots.firstOrNull {
            it.name.startsWith("kotlin") && it.name.endsWith(".jar")
        }
        var fileTemplateName = "JBang Java"
        var jbangTemplateName = "hello"
        if (groovyJarFile != null) {
            fileTemplateName = "JBang Groovy"
            jbangTemplateName = "hello.groovy"
            val groovyVersion = extractVersionFromJarFile(groovyJarFile.name) ?: "3.0.9"
            properties["GROOVY_VERSION"] = groovyVersion
            properties["GROOVY_VENDOR"] = if (groovyVersion.startsWith("4.")) {
                "apache"
            } else {
                "codehaus"
            }
        } else if (kotlinJarFile != null) {
            fileTemplateName = "JBang Kotlin"
            jbangTemplateName = "hello.kt"
            val kotlinVersion = extractVersionFromJarFile(kotlinJarFile.name) ?: "1.6.10"
            properties["KOTLIN_VERSION"] = kotlinVersion;
        }
        val roots = moduleRootManager.sourceRoots
        if (roots.isNotEmpty()) {
            val srcRoot = roots[0]
            generateScriptFrommTemplate(
                jbangTemplateName,
                jbangTemplateName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                srcRoot.toNioPath().absolutePathString()
            )
            //generateFromInternalTemplate(module, fileTemplateName, properties, srcRoot);
        }
    }

    private fun extractVersionFromJarFile(jarFileName: String): String? {
        val matcher = Pattern.compile(".*-(\\d+\\..*)\\.jar").matcher(jarFileName);
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    private fun generateFromInternalTemplate(module: Module, fileTemplateName: String, properties: Properties, srcRoot: VirtualFile) {
        val template = FileTemplateManager.getInstance(module.project).getInternalTemplate(fileTemplateName)
        ApplicationManager.getApplication().runWriteAction {
            try {
                FileTemplateUtil.createFromTemplate(
                    template, "Hello",
                    properties,
                    srcRoot.toPsiDirectory(module.project)!!
                )
            } catch (ignore: Exception) {
                // ignore IndexNotReadyException
            }
        }
    }

}