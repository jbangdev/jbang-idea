package dev.jbang.idea.run

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.icons.AllIcons
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.CommonProgramParametersPanel
import com.intellij.execution.util.ProgramParametersConfigurator
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.FormBuilder
import dev.jbang.idea.JBangPlugin
import dev.jbang.idea.cli.JBangCli
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

// --- Configuration Type ---

private val jbangRunIcon = LayeredIcon.layeredIcon(arrayOf(JBangPlugin.icon16, AllIcons.Nodes.RunnableMark))

class JBangConfigurationType : ConfigurationType {
    override fun getDisplayName() = "JBang"
    override fun getConfigurationTypeDescription() = "Run a JBang script"
    override fun getIcon() = jbangRunIcon
    override fun getId() = "JBangRunConfiguration"
    override fun getConfigurationFactories() = arrayOf(JBangConfigurationFactory(this))
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
) : RunConfigurationBase<JBangRunConfigOptions>(project, factory, name), CommonProgramRunConfigurationParameters {

    override fun getOptionsClass(): Class<JBangRunConfigOptions> = JBangRunConfigOptions::class.java

    override fun getOptions(): JBangRunConfigOptions =
        super.getOptions() as JBangRunConfigOptions

    var scriptPath: String
        get() = options.scriptPath
        set(value) { options.scriptPath = value }

    var scriptArgs: String
        get() = options.scriptArgs
        set(value) { options.scriptArgs = value }

    override fun getProgramParameters(): String = scriptArgs

    override fun setProgramParameters(value: String?) {
        scriptArgs = value.orEmpty()
    }

    var jbangOptions: String
        get() = options.jbangOptions
        set(value) { options.jbangOptions = value }

    var environmentVariables: String
        get() = options.environmentVariables
        set(value) {
            options.environmentVariables = value
            options.envs = linkedMapOf()
        }

    override fun getEnvs(): MutableMap<String, String> = LinkedHashMap(
        options.envs.ifEmpty { JBangRunState.parseEnvironment(environmentVariables) }
    )

    override fun setEnvs(envs: MutableMap<String, String>) {
        options.envs = LinkedHashMap(envs)
        options.environmentVariables = envs.entries.joinToString("\n") { (key, value) -> "$key=$value" }
    }

    override fun isPassParentEnvs(): Boolean = options.passParentEnvs

    override fun setPassParentEnvs(value: Boolean) {
        options.passParentEnvs = value
    }

    override fun getWorkingDirectory(): String = options.workingDirectory

    override fun setWorkingDirectory(value: String?) {
        options.workingDirectory = value.orEmpty()
    }

    var runInTerminal: Boolean
        get() = options.runInTerminal
        set(value) { options.runInTerminal = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = JBangSettingsEditor(project)

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
    private val _envs = map<String, String>().provideDelegate(this, "envs")
    private val _workingDirectory = string("").provideDelegate(this, "workingDirectory")
    private val _passParentEnvs = property(true).provideDelegate(this, "passParentEnvs")
    private val _runInTerminal = property(true).provideDelegate(this, "runInTerminal")

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

    var envs: MutableMap<String, String>
        get() = _envs.getValue(this)
        set(value) { _envs.setValue(this, value) }

    var workingDirectory: String
        get() = _workingDirectory.getValue(this) ?: ""
        set(value) { _workingDirectory.setValue(this, value) }

    var passParentEnvs: Boolean
        get() = _passParentEnvs.getValue(this)
        set(value) { _passParentEnvs.setValue(this, value) }

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
            val resolved = resolve(config)
            val args = mutableListOf(JBangCli.findJBangCmd(), "run")
            args += resolved.jbangOptions
            args += resolved.scriptPath
            args += resolved.scriptArgs
            return PtyCommandLine(args)
                .withParentEnvironmentType(if (resolved.passParentEnvs) {
                    GeneralCommandLine.ParentEnvironmentType.CONSOLE
                } else {
                    GeneralCommandLine.ParentEnvironmentType.NONE
                })
                .withEnvironment(resolved.environment)
                .withWorkDirectory(resolved.workingDirectory)
        }

        fun buildShellCommand(config: JBangRunConfiguration): String {
            val resolved = resolve(config)
            val command = mutableListOf(quote(JBangCli.findJBangCmd()), "run")
            command += resolved.jbangOptions.map(::quote)
            command += quote(resolved.scriptPath)
            command += resolved.scriptArgs.map(::quote)

            val environment = resolved.environment.map { (key, value) -> "$key=${quote(value)}" }.toMutableList()
            if (!resolved.passParentEnvs) environment.add(0, "env -i")
            val invocation = (environment + command).joinToString(" ")
            return "cd ${quote(resolved.workingDirectory)} && $invocation"
        }

        fun quote(value: String): String =
            if (value.isNotEmpty() && value.all { it.isLetterOrDigit() || it in "_@%+=:,./-" }) value
            else "'${value.replace("'", "'\\''")}'"

        internal fun parseEnvironment(value: String): Map<String, String> = value.splitToSequence(';', '\n')
            .map(String::trim)
            .filter { it.isNotEmpty() && '=' in it }
            .associateTo(LinkedHashMap()) { it.substringBefore('=').trim() to it.substringAfter('=') }

        internal fun resolve(config: JBangRunConfiguration): ResolvedJBangParameters {
            val configurator = ProgramParametersConfigurator()
            val common = SimpleProgramParameters()
            configurator.configureConfiguration(common, config)
            val expandedOptions = configurator.expandPathAndMacros(config.jbangOptions, null, config.project).orEmpty()
            val expandedScript = configurator.expandPathAndMacros(config.scriptPath, null, config.project).orEmpty()
            return ResolvedJBangParameters(
                scriptPath = expandedScript,
                jbangOptions = ParametersListUtil.parse(expandedOptions),
                scriptArgs = common.programParametersList.list,
                environment = common.env,
                passParentEnvs = common.isPassParentEnvs,
                workingDirectory = common.workingDirectory ?: config.project.basePath.orEmpty(),
            )
        }
    }
}

internal data class ResolvedJBangParameters(
    val scriptPath: String,
    val jbangOptions: List<String>,
    val scriptArgs: List<String>,
    val environment: Map<String, String>,
    val passParentEnvs: Boolean,
    val workingDirectory: String,
)

// --- Settings Editor ---

class JBangSettingsEditor(project: Project) : SettingsEditor<JBangRunConfiguration>() {

    private val scriptPathField = TextFieldWithBrowseButton(ExtendableTextField()).apply {
        addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
        CommonProgramParametersPanel.addMacroSupport(textField as ExtendableTextField)
    }
    private val optionsField = RawCommandLineEditor().apply {
        CommonProgramParametersPanel.addMacroSupport(editorField)
    }
    private val commonParametersPanel = CommonProgramParametersPanel(project).apply {
        setProgramParametersLabel("Arguments:")
    }
    private val terminalCheckbox = JCheckBox("Run in terminal")

    override fun createEditor(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Script:", scriptPathField)
            .addLabeledComponent("JBang options:", optionsField)
            .addComponent(commonParametersPanel)
            .addComponent(terminalCheckbox)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun applyEditorTo(config: JBangRunConfiguration) {
        config.scriptPath = scriptPathField.text
        config.jbangOptions = optionsField.text
        commonParametersPanel.applyTo(config)
        config.runInTerminal = terminalCheckbox.isSelected
    }

    override fun resetEditorFrom(config: JBangRunConfiguration) {
        scriptPathField.text = config.scriptPath
        optionsField.text = config.jbangOptions
        commonParametersPanel.reset(config)
        terminalCheckbox.isSelected = config.runInTerminal
    }
}
