package dev.jbang.intellij.plugins.jbang.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Factory
import java.io.File


class JbangRunConfiguration(
    project: Project, factory: ConfigurationFactory, name: String
) : RunConfigurationBase<JbangRunConfigurationOptions>(project, factory, name), Factory<JbangRunConfiguration> {

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

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return JbangRunSettingsEditor(this)
    }

    override fun getOptions(): JbangRunConfigurationOptions {
        return super.getOptions() as JbangRunConfigurationOptions
    }

    override fun getState(executor: Executor, executionEnvironment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(executionEnvironment) {
            override fun startProcess(): ProcessHandler {
                val command = mutableListOf("jbang", "run")
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
                    command.add("jbang")
                }
                val commandLine = GeneralCommandLine(command)
                commandLine.workDirectory = File(project.basePath!!)
                val processHandler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
                ProcessTerminatedListener.attach(processHandler)
                return processHandler
            }
        }
    }

    override fun create(): JbangRunConfiguration {
        return this
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