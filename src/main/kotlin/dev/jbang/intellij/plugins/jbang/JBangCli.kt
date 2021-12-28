package dev.jbang.intellij.plugins.jbang

import dev.jbang.cli.BaseScriptCommand
import dev.jbang.cli.Info
import dev.jbang.cli.Init
import dev.jbang.util.Util
import org.apache.commons.lang3.reflect.FieldUtils
import org.apache.commons.lang3.reflect.MethodUtils
import picocli.CommandLine
import kotlin.io.path.Path

object JBangCli {

    @Throws(Exception::class)
    fun resolveScriptDependencies(scriptFilePath: String): List<String> {
        val args = arrayOf("classpath", "--fresh", scriptFilePath)
        val demo = Info()
        val commandLine = CommandLine(demo)
        commandLine.execute(*args)
        val baseScriptCommand = commandLine.subcommands["classpath"]!!.getCommand<BaseScriptCommand>()
        val scriptInfo = MethodUtils.invokeMethod(baseScriptCommand, true, "getInfo")
        return FieldUtils.readDeclaredField(scriptInfo, "resolvedDependencies", true) as List<String>
    }

    @Throws(Exception::class)
    fun generateScriptFrommTemplate(templateName: String, scriptName: String, destDir: String) {
        val args = arrayOf("--template", templateName, "--force", scriptName)
        Util.setCwd(Path(destDir))
        val commandLine = CommandLine(Init())
        commandLine.execute(*args)
    }
}