package dev.jbang.idea.cli

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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

data class TemplateProperty(
    val description: String = "",
    @SerializedName("default") val defaultValue: String? = null,
)

data class TemplateInfo(
    val name: String = "",
    val fullName: String = "",
    val description: String = "",
    val properties: Map<String, TemplateProperty> = emptyMap(),
)

data class TemplateLookupResult(
    val reference: String,
    val template: TemplateInfo? = null,
    val catalogName: String? = null,
    val catalogRef: String? = null,
    val error: String? = null,
)

private data class TemplateCatalogInfo(
    val name: String = "",
    val resourceRef: String? = null,
    val templates: List<TemplateInfo> = emptyList(),
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
    fun resolveJBangPath(): String? = resolveJBangPath(
        settingsPath = JBangSettings.instance.jbangPath,
        isWindows = SystemInfo.isWindows,
        jbangHome = System.getenv("JBANG_HOME"),
        userHome = System.getProperty("user.home"),
        pathDirs = System.getenv("PATH")?.split(File.pathSeparatorChar).orEmpty(),
    )

    /**
     * Testable path resolution logic — all environment dependencies injected.
     */
    internal fun resolveJBangPath(
        settingsPath: String,
        isWindows: Boolean,
        jbangHome: String?,
        userHome: String,
        pathDirs: List<String>,
    ): String? {
        if (settingsPath.isNotBlank() && File(settingsPath).canExecute()) return settingsPath

        // On Windows, the extensionless "jbang" file is a bash script that
        // File.canExecute() matches but CreateProcess cannot run (error=193).
        // Always prefer jbang.cmd on Windows.
        val binaryName = if (isWindows) "jbang.cmd" else "jbang"

        if (!jbangHome.isNullOrBlank()) {
            val cmd = File(jbangHome, "bin/$binaryName").absolutePath
            if (File(cmd).canExecute()) return cmd
        }

        val userDir = File(userHome, ".jbang/bin/$binaryName")
        if (userDir.canExecute()) return userDir.absolutePath

        for (dir in pathDirs) {
            val candidate = File(dir, binaryName)
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
            val output = exec(*buildTemplateListCommand().toTypedArray())
            gson.fromJson<List<TemplateInfo>>(output, object : TypeToken<List<TemplateInfo>>() {}.type)
        } catch (e: Exception) {
            log.warn("jbang template list failed: ${e.message}")
            emptyList()
        }
    }

    internal fun buildTemplateListCommand(): List<String> =
        listOf("jbang", "template", "list", "--show-properties", "--format=json")

    fun lookupTemplate(reference: String): TemplateLookupResult {
        val catalogName = reference.substringAfterLast('@', missingDelimiterValue = "")
        if (catalogName.isBlank()) {
            return TemplateLookupResult(reference, error = "Use a catalog-qualified ID such as template@catalog")
        }
        return try {
            val output = exec(*buildTemplateLookupCommand(reference).toTypedArray())
            parseTemplateLookup(reference, output)
        } catch (e: Exception) {
            log.warn("jbang template lookup failed for $reference: ${e.message}")
            TemplateLookupResult(reference, catalogName = catalogName, error = e.message ?: "Template lookup failed")
        }
    }

    internal fun buildTemplateLookupCommand(reference: String): List<String> {
        val catalogName = reference.substringAfterLast('@', missingDelimiterValue = "")
        require(catalogName.isNotBlank()) { "Template reference must include @catalog" }
        return listOf(
            "jbang", "template", "list", catalogName,
            "--show-properties", "--show-origin", "--format=json",
        )
    }

    internal fun parseTemplateLookup(reference: String, json: String): TemplateLookupResult {
        val catalogName = reference.substringAfterLast('@', missingDelimiterValue = "")
        val catalogs = gson.fromJson<List<TemplateCatalogInfo>>(
            json,
            object : TypeToken<List<TemplateCatalogInfo>>() {}.type,
        )
        val catalog = catalogs.firstOrNull { it.name == catalogName } ?: catalogs.firstOrNull()
        val templateName = reference.substringBeforeLast('@')
        val template = catalog?.templates?.firstOrNull {
            it.fullName == reference || it.name == templateName
        }
        return TemplateLookupResult(
            reference = reference,
            template = template,
            catalogName = catalog?.name ?: catalogName,
            catalogRef = catalog?.resourceRef,
            error = if (template == null) "Template '$templateName' was not found" else null,
        )
    }

    /** Calls `jbang init [properties] [--template <template>] --force <filePath>`. */
    fun initScript(
        filePath: String,
        templateName: String? = null,
        properties: Map<String, String> = emptyMap(),
    ) {
        val wsl = if (SystemInfo.isWindows) WslPath.parseWindowsUncPath(filePath) else null
        val effectivePath = wsl?.linuxPath ?: filePath
        exec(*buildInitCommand(effectivePath, templateName, properties).toTypedArray(), wslDistributionId = wsl?.distributionId)
    }

    internal fun buildInitCommand(
        filePath: String,
        templateName: String? = null,
        properties: Map<String, String> = emptyMap(),
    ): List<String> = buildList {
        add("jbang")
        add("init")
        properties.forEach { (key, value) -> add("-D$key=$value") }
        templateName?.let {
            add("--template")
            add(it)
        }
        add("--force")
        add(filePath)
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

        log.info("exec: ${cmd.commandLineString}")
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
