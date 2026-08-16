package dev.jbang.idea.cli

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.util.SystemInfo
import dev.jbang.idea.debug
import dev.jbang.idea.jbangLog
import dev.jbang.idea.settings.JBangSettings
import java.io.File

private val log = jbangLog<JBangCli>()

// --- Data classes for `jbang info tools` JSON output ---

data class SourceEntry(
    val originalResource: String = "",
    val backingResource: String = "",
    val error: String? = null
)

data class ResourceEntry(
    val originalResource: String = "",
    val backingResource: String = "",
    val target: String? = null,
    val error: String? = null,
)

data class ScriptInfo(
    val originalResource: String = "",
    val backingResource: String = "",
    val applicationJar: String? = null,
    val dependencies: List<String> = emptyList(),
    val resolvedDependencies: List<String> = emptyList(),
    val javaVersion: String? = null,
    val requestedJavaVersion: String? = null,
    val availableJdkPath: String? = null,
    val compileOptions: List<String> = emptyList(),
    val runtimeOptions: List<String> = emptyList(),
    val sources: List<SourceEntry> = emptyList(),
    val files: List<ResourceEntry> = emptyList(),
    val commandErrors: List<String> = emptyList(),
) {
    /** All resolved JAR paths for the classpath. */
    val classpathJars: List<String>
        get() = resolvedDependencies.ifEmpty {
            // Fallback: applicationJar alone when deps didn't resolve
            listOfNotNull(applicationJar)
        }

    val dependencyErrors: List<String>
        get() = if (dependencies.isNotEmpty() && resolvedDependencies.isEmpty()) {
            dependencies.map { "Unable to resolve dependency: $it" }
        } else emptyList()

    val resolutionErrors: List<String>
        get() = commandErrors + dependencyErrors + sources.mapNotNull { it.error } + files.mapNotNull { it.error }
}

data class TemplateInfo(
    val name: String = "",
    val description: String = "",
)

private val gson = Gson()

/** Strip any JVM diagnostic lines before the JSON object. */
private fun String.trimToJson(): String {
    val start = indexOf('{')
    return if (start > 0) substring(start) else this
}

// --- CLI invocation ---

object JBangCli {

    /**
     * Resolve the absolute path to the `jbang` command.
     * Checks: settings → JBANG_HOME/bin → ~/.jbang/bin → PATH.
     */
    fun findJBangCmd(): String {
        val fromSettings = JBangSettings.instance.jbangPath
        if (fromSettings.isNotBlank() && File(fromSettings).canExecute()) return fromSettings

        val jbangHome = System.getenv("JBANG_HOME")
        if (!jbangHome.isNullOrBlank()) {
            val cmd = File(jbangHome, "bin/jbang").absolutePath
            if (File(cmd).canExecute()) return cmd
        }

        val userDir = File(System.getProperty("user.home"), ".jbang/bin/jbang")
        if (userDir.canExecute()) return userDir.absolutePath

        // Fall back to PATH
        return if (SystemInfo.isWindows) "jbang.cmd" else "jbang"
    }

    /**
     * Calls `jbang info tools --quiet <scriptPath>` and parses the JSON.
     * Returns error details in [ScriptInfo.commandErrors] when JBang fails.
     */
    fun resolveScriptInfo(scriptPath: String): ScriptInfo? {
        return try {
            val output = exec(findJBangCmd(), "info", "tools", "--quiet", scriptPath,
                env = mapOf("JBANG_DOWNLOAD_SOURCES" to "true"),
                workDirectory = File(scriptPath).absoluteFile.parentFile)
            gson.fromJson(output.trimToJson(), ScriptInfo::class.java)
        } catch (e: Exception) {
            val error = e.message ?: "jbang info tools failed"
            log.warn("jbang info tools failed for $scriptPath: $error")
            ScriptInfo(originalResource = scriptPath, commandErrors = listOf(error))
        }
    }

    /**
     * Calls `jbang template list --format=json` and returns available templates.
     */
    fun listTemplates(): List<TemplateInfo> {
        return try {
            val output = exec(findJBangCmd(), "template", "list", "--format=json")
            gson.fromJson<List<TemplateInfo>>(output, object : TypeToken<List<TemplateInfo>>() {}.type)
        } catch (e: Exception) {
            log.warn("jbang template list failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Calls `jbang init --template <template> --force <filePath>`.
     */
    fun initScript(templateName: String, filePath: String) {
        exec(findJBangCmd(), "init", "--template", templateName, "--force", filePath)
    }

    fun initScript(filePath: String) {
        exec(findJBangCmd(), "init", "--force", filePath)
    }

    private fun exec(
        vararg command: String,
        env: Map<String, String> = emptyMap(),
        workDirectory: File? = null,
    ): String {
        val cmd = GeneralCommandLine(*command)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment("NO_COLOR", "true")
            .withEnvironment(env)
            .withWorkDirectory(workDirectory)

        log.debug { "exec: ${command.joinToString(" ")}" }
        val handler = CapturingProcessHandler(cmd)
        val result = handler.runProcess(30_000)

        if (result.exitCode != 0) {
            val stderr = result.stderr.trim()
            error("jbang exited ${result.exitCode}: $stderr")
        }
        return result.stdout
    }
}
