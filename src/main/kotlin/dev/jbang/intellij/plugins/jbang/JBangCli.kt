package dev.jbang.intellij.plugins.jbang

import java.io.BufferedReader
import java.io.File

object JBangCli {

    @Throws(Exception::class)
    fun listJbangTemplates(): List<String> {
        val jbangCmd = getJBangCmdAbsolutionPath()
        val pb = ProcessBuilder(jbangCmd, "template", "list")
        pb.environment()["NO_COLOR"] = "true"
        val process = pb.start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw Exception(processErrorToText(process))
        } else {
            return processOutputToText(process).lines()
        }
    }

    @Throws(Exception::class)
    fun resolveScriptDependencies(scriptFilePath: String): List<String> {
        val jbangCmd = getJBangCmdAbsolutionPath()
        val pb = ProcessBuilder(jbangCmd, "info", "classpath", "--fresh", scriptFilePath)
        val process = pb.start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw Exception(processErrorToText(process))
        } else {
            return processOutputToText(process).split(':', ';').filter { !it.contains(".jbang") }
        }
    }

    @Throws(Exception::class)
    fun generateScriptFrommTemplate(templateName: String, scriptName: String, destDir: String) {
        val jbangCmd = getJBangCmdAbsolutionPath()
        val filePath = File(destDir, scriptName).absolutePath
        val pb = ProcessBuilder(jbangCmd, "init", "--template", templateName, "--force", filePath)
        val process = pb.start()
        process.waitFor()
    }

    private fun processOutputToText(process: Process): String {
        return process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
    }

    private fun processErrorToText(process: Process): String {
        return process.errorStream.bufferedReader().use(BufferedReader::readText).trim()
    }
}