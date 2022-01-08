package dev.jbang.idea.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import dev.jbang.idea.getJBangCmdAbsolutionPath
import dev.jbang.idea.jbangIcon
import java.io.File
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
                if (scriptName != null && scriptName.isNotEmpty()) {
                    if (options != null && options.isNotEmpty()) {
                        command.addAll(options.split("\\s+".toRegex()).filter { it.isNotEmpty() })
                    }
                    command.add(scriptName)
                    if (args != null && args.isNotEmpty()) {
                        command.addAll(args.split("\\s+".toRegex()).filter { it.isNotEmpty() })
                    }
                } else {
                    command.clear()
                    command.add(jbangCmd)
                }
                val commandLine = GeneralCommandLine(command)
                commandLine.workDirectory = File(project.basePath!!)
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
        if (scriptName == null || scriptName.isEmpty()) {
            throw RuntimeConfigurationException("Script name is empty")
        } else {
            val scriptFile = File(project.basePath!!, scriptName)
            if (!scriptFile.exists()) {
                throw RuntimeConfigurationException("Script file does not exist: $scriptName")
            }
        }
    }

}