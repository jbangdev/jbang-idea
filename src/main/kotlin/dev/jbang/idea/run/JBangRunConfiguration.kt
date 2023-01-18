package dev.jbang.idea.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import dev.jbang.idea.getJBangCmdAbsolutionPath
import dev.jbang.idea.jbangIcon
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.swing.Icon


class JBangRunConfiguration(
    project: Project, factory: ConfigurationFactory, name: String
) : RunConfigurationBase<JBangRunConfigurationOptions>(project, factory, name) {

    fun getScriptName(): String? {
        return options.getScriptName()
    }

    fun setScriptName(scriptName: String?) {
        options.setScriptName(scriptName)
    }

    fun getScriptOptions(): String? {
        return options.getScriptOptions()
    }

    fun setScriptOptions(scriptOptions: String?) {
        options.setScriptOptions(scriptOptions)
    }

    fun getScriptArgs(): String? {
        return options.getScriptArgs()
    }

    fun setScriptArgs(scriptArgs: String) {
        options.setScriptArgs(scriptArgs)
    }

    fun getEnvVariables(): String? {
        return options.getEnvVariables()
    }

    fun setEnvVariables(envVariables: String) {
        options.setEnvVariables(envVariables)
    }

    fun getEnvVariablesAsMap(): Map<String, String> {
        val variables = getEnvVariables()
        if (variables != null && variables.contains('=')) {
            val variablesMap = mutableMapOf<String, String>()
            //pairs like NAME=xxx or NAME="a b c d"
            val p = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|(\\S+))\"*")
            val m: Matcher = p.matcher(variables)
            while (m.find()) {
                val name = m.group(1)
                val value = m.group(2)
                variablesMap[name.uppercase()] = value
            }
            return variablesMap
        }
        return emptyMap()
    }

    override fun getConfigurationEditor(): JBangRunSettingsEditor {
        return JBangRunSettingsEditor()
    }

    override fun getOptions(): JBangRunConfigurationOptions {
        return super.getOptions() as JBangRunConfigurationOptions
    }

    override fun getIcon(): Icon {
        return jbangIcon
    }

    override fun getState(executor: Executor, executionEnvironment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(executionEnvironment) {
            override fun startProcess(): ProcessHandler {
                var jbangCmd = getJBangCmdAbsolutionPath()
                if (!File(jbangCmd).exists()) {
                    jbangCmd = "jbang"
                }
                val command = mutableListOf(jbangCmd, "run")
                val scriptName = getScriptName()
                val options = getScriptOptions()
                val args = getScriptArgs()
                if (!scriptName.isNullOrEmpty()) {
                    if (!options.isNullOrEmpty()) {
                        command.addAll(ParametersListUtil.parse(options, false, true, false))
                    }
                    command.add(scriptName)
                    if (!args.isNullOrEmpty()) {
                        command.addAll(ParametersListUtil.parse(args, false, true, false))
                    }
                } else {
                    command.clear()
                    command.add(jbangCmd)
                }
                val commandLine = GeneralCommandLine(command)
                commandLine.workDirectory = File(project.basePath!!)
                commandLine.environment.putAll(getEnvVariablesAsMap())
                val processHandler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine) as ColoredProcessHandler
                processHandler.setShouldKillProcessSoftly(true)
                ProcessTerminatedListener.attach(processHandler)
                return processHandler
            }
        }
    }

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        val scriptName = getScriptName()
        if (scriptName.isNullOrEmpty()) {
            throw RuntimeConfigurationException("Script name is empty")
        } else if (!scriptName.contains('@')) {
            val scriptFile = if (scriptName.startsWith("/") || scriptName.contains(":")) {
                File(scriptName)
            } else {
                File(project.basePath!!, "/$scriptName")
            }
            if (!scriptFile.exists()) {
                throw RuntimeConfigurationException("Script file does not exist: $scriptName")
            }
        }
    }

}
