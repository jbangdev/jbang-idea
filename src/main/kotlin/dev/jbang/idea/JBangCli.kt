package dev.jbang.idea

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
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
            .command(jbangCmd, "template", "list", "--format=json")
            .environment(parentEnvironment())
            .environment("NO_COLOR", "true")
            .readOutput(true)
            .execute()
            .outputUTF8()

        val objectMapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
        return objectMapper.readValue(output)
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
        return output.split(File.pathSeparator).filter { !it.contains(".jbang") }.map { it.trim() }
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
        return objectMapper.readValue(allText)
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