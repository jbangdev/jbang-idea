package dev.jbang.idea

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import dev.jbang.idea.JBangCli.parentEnvironment
import org.zeroturnaround.exec.ProcessExecutor
import java.io.File

public val PARENT_ENV_VAR = parentEnvironment();

object JBangCli {

    private val log: Logger = Logger.getInstance(JBangCli::class.java)

    @Throws(Exception::class)
    fun listJBangTemplates(): List<TemplateInfo> {
        val jbangCmd = getJBangCmdAbsolutionPath()
        val output = ProcessExecutor()
            .command(jbangCmd, "template", "list", "--format=json", "--quiet")
            .environment(parentEnvironment())
            .environment("NO_COLOR", "true")
            .readOutput(true)
            .execute()
            .outputUTF8()

        val objectMapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
        return objectMapper.readValue(cleanOutput(output, true))
    }

    internal fun cleanOutput(output: String, isJson: Boolean = false): String {
        val lines = output.lines()
        if (isJson) {
            val jsonStartIndex = lines.indexOfFirst { line ->
                val trimmed = line.trimStart()
                // Heuristic: Is this the start of a JSON payload?
                // 1. Starts with "{" (JVM warnings almost never do)
                // 2. Starts with "[" BUT is followed by a valid JSON object or string start like "{", '"', an empty array "]", or is just empty.
                // Note: This intentionally ignores JSON arrays of primitives like `[1]` or `[true]` 
                // because JBang only ever outputs arrays of objects `[{...}]` or strings `["..."]`.
                trimmed.startsWith("{") || 
                    (trimmed.startsWith("[") && trimmed.substring(1).trimStart().let { it.isEmpty() || it.startsWith("{") || it.startsWith("\"") || it.startsWith("]") })
            }
            return if (jsonStartIndex >= 0) {
                lines.subList(jsonStartIndex, lines.size).joinToString("\n")
            } else {
                output
            }
        }

        // For classpath, we expect the payload to be the last non-empty line.
        // JVM warnings are printed before it.
        return lines.lastOrNull { it.isNotBlank() }?.trim() ?: output
    }

    @Throws(Exception::class)
    fun resolveScriptDependencies(scriptFilePath: String): List<String> {
        val jbangCmd = getJBangCmdAbsolutionPath()
        val output = ProcessExecutor()
            .command(jbangCmd, "info", "classpath", "--quiet", "--fresh", scriptFilePath)
            .environment(parentEnvironment())
            .environment("JBANG_DOWNLOAD_SOURCES", "true")
            .readOutput(true)
            .execute()
            .outputUTF8()
        return cleanOutput(output).split(File.pathSeparator).filter { !it.contains(".jbang") }.map { it.trim() }
    }

    @Throws(Exception::class)
    fun resolveScriptInfo(jbangScriptFilePath: String): ScriptInfo {
        val jbangCmd = getJBangCmdAbsolutionPath()
        val allText = ProcessExecutor()
            .command(jbangCmd, "info", "tools", "--quiet", "--fresh", jbangScriptFilePath)
            .environment(parentEnvironment())
            .environment("JBANG_DOWNLOAD_SOURCES", "true")
            .readOutput(true)
            .execute()
            .outputUTF8()
        val objectMapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
        return objectMapper.readValue(cleanOutput(allText, true))
    }

    @Throws(Exception::class)
    fun generateScriptFromTemplate(templateName: String, scriptName: String, destDir: String) {
        val jbangCmd = getJBangCmdAbsolutionPath()
        val filePath = File(destDir, scriptName).absolutePath
        val output = ProcessExecutor()
            .command(jbangCmd, "init", "--template", templateName, "--force", filePath)
            .environment(parentEnvironment())
            .readOutput(true)
            .execute()
            .outputUTF8()
        log.info(output)
    }

    fun parentEnvironment(): Map<String, String> {
       return GeneralCommandLine().withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE).effectiveEnvironment
    }

}
