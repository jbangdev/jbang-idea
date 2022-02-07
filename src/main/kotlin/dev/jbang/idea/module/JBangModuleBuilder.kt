package dev.jbang.idea.module

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleBuilderListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import dev.jbang.idea.JBangCli.generateScriptFromTemplate
import dev.jbang.idea.jbangIcon
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
        val groovyJarFile = moduleRootManager.orderEntries().allLibrariesAndSdkClassesRoots.firstOrNull {
            it.name.startsWith("groovy") && it.name.endsWith(".jar")
        }
        val kotlinJarFile = moduleRootManager.orderEntries().allLibrariesAndSdkClassesRoots.firstOrNull {
            it.name.startsWith("kotlin") && it.name.endsWith(".jar")
        }
        var jbangTemplateName = "hello"
        var jbangScriptFile = "Hello.java"
        if (groovyJarFile != null) {
            jbangTemplateName = "hello.groovy"
            jbangScriptFile = "Hello.groovy"
            val groovyVersion = extractVersionFromJarFile(groovyJarFile.name) ?: "3.0.9"
            properties["GROOVY_VERSION"] = groovyVersion
            properties["GROOVY_VENDOR"] = if (groovyVersion.startsWith("4.")) {
                "apache"
            } else {
                "codehaus"
            }
        } else if (kotlinJarFile != null) {
            jbangTemplateName = "hello.kt"
            jbangScriptFile = "Hello.kt"
            val kotlinVersion = extractVersionFromJarFile(kotlinJarFile.name) ?: "1.6.10"
            properties["KOTLIN_VERSION"] = kotlinVersion
        }
        val roots = moduleRootManager.sourceRoots
        if (roots.isNotEmpty()) {
            val srcRoot = roots[0]
            generateScriptFromTemplate(
                jbangTemplateName,
                jbangScriptFile,
                srcRoot.toNioPath().absolutePathString()
            )
        }
    }

    private fun extractVersionFromJarFile(jarFileName: String): String? {
        val matcher = Pattern.compile(".*-(\\d+\\..*)\\.jar").matcher(jarFileName)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

}