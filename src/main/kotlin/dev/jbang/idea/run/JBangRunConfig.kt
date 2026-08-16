package dev.jbang.idea.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.executors.DefaultDebugExecutor
import javax.swing.Icon
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBTextField
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.FormBuilder
import dev.jbang.idea.cli.JBangCli
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

// --- Configuration Type ---

class JBangConfigurationType : ConfigurationType {
    override fun getDisplayName() = "JBang"
    override fun getConfigurationTypeDescription() = "Run a JBang script"
    override fun getIcon() = JBangRunIcons.configIcon
    override fun getId() = "JBangRunConfiguration"
    override fun getConfigurationFactories() = arrayOf(JBangConfigurationFactory(this))
}

object JBangRunIcons {
    /** JBang icon with a small run triangle overlay in the bottom-right corner. */
    val configIcon: Icon by lazy {
        com.intellij.ui.LayeredIcon.layeredIcon(arrayOf(
            dev.jbang.idea.JBangPlugin.icon16,
            com.intellij.icons.AllIcons.Nodes.RunnableMark
        ))
    }
}

class JBangConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId() = "JBangConfigurationFactory"
    override fun createTemplateConfiguration(project: Project) = JBangRunConfiguration(project, this, "JBang")
}

// --- Run Configuration ---

class JBangRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<JBangRunConfigOptions>(project, factory, name) {

    override fun getOptionsClass(): Class<JBangRunConfigOptions> = JBangRunConfigOptions::class.java

    override fun getOptions(): JBangRunConfigOptions =
        super.getOptions() as JBangRunConfigOptions

    var scriptPath: String
        get() = options.scriptPath
        set(value) { options.scriptPath = value }

    var scriptArgs: String
        get() = options.scriptArgs
        set(value) { options.scriptArgs = value }

    var jbangOptions: String
        get() = options.jbangOptions
        set(value) { options.jbangOptions = value }

    var environmentVariables: String
        get() = options.environmentVariables
        set(value) { options.environmentVariables = value }

    var workingDirectory: String
        get() = options.workingDirectory
        set(value) { options.workingDirectory = value }

    var runInTerminal: Boolean
        get() = options.runInTerminal
        set(value) { options.runInTerminal = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = JBangSettingsEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        val isDebug = executor.id == DefaultDebugExecutor.EXECUTOR_ID
        if (isDebug && runInTerminal && !SystemInfo.isWindows) {
            return JBangTerminalDebugRunState(this, environment)
        }
        if (isDebug) {
            return JBangDebugRunState(this, environment)
        }
        if (runInTerminal && !SystemInfo.isWindows) {
            return JBangTerminalRunState(this, environment)
        }
        return JBangRunState(this, environment)
    }
}

class JBangRunConfigOptions : RunConfigurationOptions() {
    private val _scriptPath = string("").provideDelegate(this, "scriptPath")
    private val _scriptArgs = string("").provideDelegate(this, "scriptArgs")
    private val _jbangOptions = string("").provideDelegate(this, "jbangOptions")
    private val _environmentVariables = string("").provideDelegate(this, "environmentVariables")
    private val _workingDirectory = string("").provideDelegate(this, "workingDirectory")
    private val _runInTerminal = property(false).provideDelegate(this, "runInTerminal")

    var scriptPath: String
        get() = _scriptPath.getValue(this) ?: ""
        set(value) { _scriptPath.setValue(this, value) }

    var scriptArgs: String
        get() = _scriptArgs.getValue(this) ?: ""
        set(value) { _scriptArgs.setValue(this, value) }

    var jbangOptions: String
        get() = _jbangOptions.getValue(this) ?: ""
        set(value) { _jbangOptions.setValue(this, value) }

    var environmentVariables: String
        get() = _environmentVariables.getValue(this) ?: ""
        set(value) { _environmentVariables.setValue(this, value) }

    var workingDirectory: String
        get() = _workingDirectory.getValue(this) ?: ""
        set(value) { _workingDirectory.setValue(this, value) }

    var runInTerminal: Boolean
        get() = _runInTerminal.getValue(this)
        set(value) { _runInTerminal.setValue(this, value) }
}

// --- Run State (executes the process) ---

class JBangRunState(private val config: JBangRunConfiguration, environment: ExecutionEnvironment) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val cmd = buildCommandLine(config)
        val handler = KillableColoredProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    companion object {
        fun buildCommandLine(config: JBangRunConfiguration): GeneralCommandLine {
            val jbang = JBangCli.findJBangCmd()
            val args = mutableListOf(jbang, "run")
            args += ParametersListUtil.parse(config.jbangOptions)
            args += config.scriptPath
            args += ParametersListUtil.parse(config.scriptArgs)
            return PtyCommandLine(args)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                .withEnvironment(parseEnvironment(config.environmentVariables))
                .withWorkDirectory(config.workingDirectory.ifBlank {
                    java.io.File(config.scriptPath).absoluteFile.parent ?: config.project.basePath
                })
        }

        fun buildShellCommand(config: JBangRunConfiguration): String {
            val jbang = quote(JBangCli.findJBangCmd())
            val script = quote(config.scriptPath)
            val parts = parseEnvironment(config.environmentVariables).map { (key, value) -> "$key=${quote(value)}" }.toMutableList()
            parts += jbang
            parts += "run"
            parts += ParametersListUtil.parse(config.jbangOptions).map(::quote)
            parts += script
            parts += ParametersListUtil.parse(config.scriptArgs).map(::quote)
            return parts.joinToString(" ")
        }

        fun quote(value: String): String =
            if (value.any { it.isWhitespace() || it in "'\"()" }) "'${value.replace("'", "'\\''")}'" else value

        private fun parseEnvironment(value: String): Map<String, String> = value.splitToSequence(';', '\n')
            .map(String::trim)
            .filter { it.isNotEmpty() && '=' in it }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=') }
    }
}

// --- Settings Editor ---

class JBangSettingsEditor : SettingsEditor<JBangRunConfiguration>() {

    private val scriptPathField = TextFieldWithBrowseButton()
    private val optionsField = JBTextField()
    private val argsField = JBTextField()
    private val environmentField = JBTextField()
    private val workingDirectoryField = TextFieldWithBrowseButton()
    private val terminalCheckbox = JCheckBox("Run in terminal")

    override fun createEditor(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Script:", scriptPathField)
            .addLabeledComponent("JBang options:", optionsField)
            .addLabeledComponent("Arguments:", argsField)
            .addLabeledComponent("Environment (KEY=value;...):", environmentField)
            .addLabeledComponent("Working directory:", workingDirectoryField)
            .addComponent(terminalCheckbox)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun applyEditorTo(config: JBangRunConfiguration) {
        config.scriptPath = scriptPathField.text
        config.jbangOptions = optionsField.text
        config.scriptArgs = argsField.text
        config.environmentVariables = environmentField.text
        config.workingDirectory = workingDirectoryField.text
        config.runInTerminal = terminalCheckbox.isSelected
    }

    override fun resetEditorFrom(config: JBangRunConfiguration) {
        scriptPathField.text = config.scriptPath
        optionsField.text = config.jbangOptions
        argsField.text = config.scriptArgs
        environmentField.text = config.environmentVariables
        workingDirectoryField.text = config.workingDirectory
        terminalCheckbox.isSelected = config.runInTerminal
    }
}
