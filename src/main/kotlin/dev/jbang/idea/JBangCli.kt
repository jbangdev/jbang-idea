package dev.jbang.idea

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.zeroturnaround.exec.ProcessExecutor
import java.io.File

object JBangCli {

    @Throws(Exception::class)
    fun listJBangTemplates(): List<String> {
        val jbangCmd = getJBangCmdAbsolutionPath()
        return ProcessExecutor().command(jbangCmd, "template", "list")
            .environment("NO_COLOR", "true")
            .readOutput(true)
            .execute()
            .outputUTF8()
            .lines()
    }

    @Throws(Exception::class)
    fun resolveScriptDependencies(scriptFilePath: String): List<String> {
        val jbangCmd = getJBangCmdAbsolutionPath()
        val output = ProcessExecutor().environment("JBANG_DOWNLOAD_SOURCES", "true").command(jbangCmd, "info", "classpath", "--quiet", "--fresh", scriptFilePath)
            .readOutput(true)
            .execute()
            .outputUTF8()
        return output.split(File.pathSeparator).filter { !it.contains(".jbang") }.map { it.trim() }
    }

    @Throws(Exception::class)
    fun resolveScriptInfo(jbangScriptFilePath: String): ScriptInfo {
        val jbangCmd = getJBangCmdAbsolutionPath()
        val allText = ProcessExecutor().environment("JBANG_DOWNLOAD_SOURCES", "true").command(jbangCmd, "info", "tools", "--quiet", "--fresh", jbangScriptFilePath)
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
        ProcessExecutor()
            .command(jbangCmd, "init", "--template", templateName, "--force", filePath)
            .execute()
    }

}