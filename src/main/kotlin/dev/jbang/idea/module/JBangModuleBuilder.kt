package dev.jbang.idea.module

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleBuilderListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import dev.jbang.idea.JBangCli.generateScriptFromTemplate
import dev.jbang.idea.jbangIcon
import org.jetbrains.kotlin.idea.base.util.sdk
import java.io.File
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
        val javaVersion = detectJavaVersion(module)
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

    /**
     * Detects Java version following jbang's logic:
     * 1. First check if IntelliJ has an SDK set up for new projects (module SDK)
     * 2. If not, check JAVA_HOME environment variable
     * 3. If not, check java command from PATH
     * 4. Otherwise default to Java 17
     */
    private fun detectJavaVersion(module: Module): Int {
        // First priority: Check if IntelliJ has an SDK set up for new projects
        val moduleSdk = module.sdk
        if (moduleSdk != null) {
            val javaVersion = extractJavaVersionFromSdk(moduleSdk.versionString)
            if (javaVersion != null) {
                return javaVersion
            }
        }

        // Second priority: Check JAVA_HOME
        val javaHome = System.getenv("JAVA_HOME")
        if (javaHome != null) {
            val javaVersion = getJavaVersionFromPath(javaHome)
            if (javaVersion != null) {
                return javaVersion
            }
        }

        // Third priority: Check java from PATH
        val javaVersion = getJavaVersionFromPathCommand()
        if (javaVersion != null) {
            return javaVersion
        }

        // Default: Java 17
        return 17
    }

    /**
     * Extracts Java version number from SDK version string (e.g., "1.8", "11", "17", "21")
     */
    private fun extractJavaVersionFromSdk(versionString: String?): Int? {
        if (versionString == null) return null
        
        // Try to match patterns like "1.8", "11", "17", "21", etc.
        // Handle both old format (1.8) and new format (11+)
        val versionPattern = Pattern.compile("(?:1\\.)?(\\d+)(?:\\.\\d+)?")
        val matcher = versionPattern.matcher(versionString)
        if (matcher.find()) {
            val majorVersion = matcher.group(1).toIntOrNull()
            if (majorVersion != null && majorVersion >= 8) {
                return majorVersion
            }
        }
        return null
    }

    /**
     * Gets Java version from a Java installation path
     */
    private fun getJavaVersionFromPath(javaHome: String): Int? {
        val javaExecutable = File(javaHome, if (System.getProperty("os.name").lowercase().contains("win")) {
            "bin/java.exe"
        } else {
            "bin/java"
        })
        
        if (javaExecutable.exists() && javaExecutable.canExecute()) {
            return getJavaVersionFromExecutable(javaExecutable.absolutePath)
        }
        return null
    }

    /**
     * Gets Java version by running java -version command
     */
    private fun getJavaVersionFromPathCommand(): Int? {
        val javaCommand = if (System.getProperty("os.name").lowercase().contains("win")) {
            "java.exe"
        } else {
            "java"
        }
        return getJavaVersionFromExecutable(javaCommand)
    }

    /**
     * Executes java -version and parses the version number
     */
    private fun getJavaVersionFromExecutable(javaPath: String): Int? {
        try {
            val process = ProcessBuilder(javaPath, "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            // Parse version from output like:
            // "openjdk version "11.0.1" 2018-10-16"
            // "java version "1.8.0_181""
            // "openjdk version "17" 2021-09-14"
            val versionPattern = Pattern.compile("version \"(?:1\\.)?(\\d+)(?:\\.\\d+)?")
            val matcher = versionPattern.matcher(output)
            if (matcher.find()) {
                val majorVersion = matcher.group(1).toIntOrNull()
                if (majorVersion != null && majorVersion >= 8) {
                    return majorVersion
                }
            }
        } catch (e: Exception) {
            // Ignore errors and return null
        }
        return null
    }

}