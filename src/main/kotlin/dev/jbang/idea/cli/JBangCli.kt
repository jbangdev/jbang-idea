package dev.jbang.idea.cli

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WslPath
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
    fun findJBangCmd(): String = resolveJBangPath() ?: if (SystemInfo.isWindows) "jbang.cmd" else "jbang"

    /**
     * Resolves the actual jbang executable path, or null if not found.
     * Checks: settings → JBANG_HOME/bin → ~/.jbang/bin → PATH lookup.
     */
    fun resolveJBangPath(): String? {
        val fromSettings = JBangSettings.instance.jbangPath
        if (fromSettings.isNotBlank() && File(fromSettings).canExecute()) return fromSettings

        val jbangHome = System.getenv("JBANG_HOME")
        if (!jbangHome.isNullOrBlank()) {
            val cmd = File(jbangHome, "bin/jbang").absolutePath
            if (File(cmd).canExecute()) return cmd
        }

        val userDir = File(System.getProperty("user.home"), ".jbang/bin/jbang")
        if (userDir.canExecute()) return userDir.absolutePath

        // Check PATH
        val pathName = if (SystemInfo.isWindows) "jbang.cmd" else "jbang"
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparatorChar).orEmpty()
        for (dir in pathDirs) {
            val candidate = File(dir, pathName)
            if (candidate.canExecute()) return candidate.absolutePath
        }

        return null
    }

    /**
     * Detects shim errors from tool managers (mise, asdf, sdkman) and returns
     * a user-friendly message, or null if the error is not a shim issue.
     */
    private fun parseShimError(stderr: String): String? {
        if (stderr.contains("No version is set for shim") || stderr.contains("no aqua-registry")) {
            return "JBang is not configured in your tool manager (mise/asdf). " +
                "Run 'mise use -g jbang@latest' or install JBang directly from Settings > Tools > JBang."
        }
        if (stderr.contains("not currently installed") || stderr.contains("not available")) {
            return "JBang shim found but not installed. Install it from Settings > Tools > JBang."
        }
        return null
    }

    /**
     * Finds a usable JAVA_HOME from IntelliJ's registered JDKs.
     * Used to bootstrap jbang when JAVA_HOME isn't in the process environment.
     */
    private fun findIdeaJavaHome(project: com.intellij.openapi.project.Project?): String? {
        return try {
            // Prefer the current project's SDK
            if (project != null) {
                com.intellij.openapi.roots.ProjectRootManager.getInstance(project).projectSdk
                    ?.takeIf { it.sdkType.name == "JavaSDK" }
                    ?.homePath
                    ?.let { return it }
            }
            // Fall back to any registered JDK
            com.intellij.openapi.projectRoots.ProjectJdkTable.getInstance().allJdks
                .filter { it.sdkType.name == "JavaSDK" && it.homePath != null }
                .maxByOrNull { it.versionString ?: "" }
                ?.homePath
        } catch (_: Exception) {
            null
        }
    }

    /** Platform-appropriate shell command to install JBang. */
    fun installCommand(): String = if (SystemInfo.isWindows)
        "iex \"& { \$(iwr -useb https://ps.jbang.dev) } app setup\""
    else
        "curl -Ls https://sh.jbang.dev | bash -s - app setup"

    /**
     * Calls `jbang info tools --quiet <scriptPath>` and parses the JSON.
     * Returns error details in [ScriptInfo.commandErrors] when JBang fails.
     */
    fun resolveScriptInfo(scriptPath: String, project: com.intellij.openapi.project.Project? = null): ScriptInfo? {
        return try {
            val wsl = if (SystemInfo.isWindows) WslPath.parseWindowsUncPath(scriptPath) else null
            val effectivePath = wsl?.linuxPath ?: scriptPath
            val output = exec("jbang", "info", "tools", "--quiet", effectivePath,
                env = mapOf("JBANG_DOWNLOAD_SOURCES" to "true"),
                workDirectory = if (wsl != null) null else File(scriptPath).absoluteFile.parentFile,
                wslDistributionId = wsl?.distributionId,
                project = project)
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

    /** Calls `jbang init [--template <template>] --force <filePath>`. */
    fun initScript(filePath: String, templateName: String? = null) {
        val wsl = if (SystemInfo.isWindows) WslPath.parseWindowsUncPath(filePath) else null
        val effectivePath = wsl?.linuxPath ?: filePath
        val templateArgs = templateName?.let { arrayOf("--template", it) }.orEmpty()
        exec("jbang", "init", *templateArgs, "--force", effectivePath, wslDistributionId = wsl?.distributionId)
    }

    private fun exec(
        vararg command: String,
        env: Map<String, String> = emptyMap(),
        workDirectory: File? = null,
        wslDistributionId: String? = null,
        project: com.intellij.openapi.project.Project? = null,
    ): String {
        val isWsl = wslDistributionId != null
        val exeCommand = if (isWsl) command else arrayOf(findJBangCmd(), *command.drop(1).toTypedArray())
        var cmd = GeneralCommandLine(*exeCommand)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment("NO_COLOR", "true")
            .withEnvironment(env)
        // If JAVA_HOME isn't set (e.g. IDEA launched from desktop shortcut),
        // inject one from IntelliJ's known JDKs so jbang can bootstrap itself.
        if (cmd.environment["JAVA_HOME"] == null && System.getenv("JAVA_HOME") == null) {
            findIdeaJavaHome(project)?.let { cmd.withEnvironment("JAVA_HOME", it) }
        }
        if (workDirectory != null) cmd = cmd.withWorkDirectory(workDirectory)

        if (isWsl && SystemInfo.isWindows) {
            try {
                val distro = WslPath.getDistributionByWindowsUncPath("//wsl.localhost/$wslDistributionId/")
                if (distro != null) {
                    cmd = distro.patchCommandLine(cmd, null, WSLCommandLineOptions())
                }
            } catch (e: Exception) {
                log.warn("WSL command patching failed: ${e.message}")
            }
        }

        log.debug { "exec: ${cmd.commandLineString}" }
        val handler = CapturingProcessHandler(cmd)
        val result = handler.runProcess(30_000)

        if (result.exitCode != 0) {
            val stderr = result.stderr.trim()
            val message = parseShimError(stderr) ?: "jbang exited ${result.exitCode}: $stderr"
            error(message)
        }
        return result.stdout
    }
}
