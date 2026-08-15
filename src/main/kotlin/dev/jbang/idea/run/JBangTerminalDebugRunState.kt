package dev.jbang.idea.run

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.execution.remote.RemoteConfigurationType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.ApplicationManager
import dev.jbang.idea.debug
import dev.jbang.idea.jbangLog
import java.io.File
import java.net.Socket

private val log = jbangLog<JBangTerminalDebugRunState>()

/**
 * Debug in Terminal: launches `jbang run --debug=4004 <script>` in the Terminal
 * tool window, then polls for the debug port to open and auto-attaches the debugger.
 */
class JBangTerminalDebugRunState(
    private val config: JBangRunConfiguration,
    private val environment: ExecutionEnvironment
) : RunProfileState {

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
        val port = JBangDebugRunState.DEFAULT_DEBUG_PORT
        val shellCmd = JBangDebugRunState.buildDebugShellCommand(config, port)
        val tabName = "jbang debug ${File(config.scriptPath).name}"

        try {
            val project = config.project
            val twm = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            val toolWindow = twm.getToolWindow("Terminal")
            if (toolWindow == null) {
                log.warn("Terminal tool window not found")
                // Fall back to non-terminal debug
                return JBangDebugRunState(config, environment).execute(executor, runner)
            }

            ApplicationManager.getApplication().invokeLater {
                toolWindow.activate {
                    val manager = org.jetbrains.plugins.terminal.TerminalToolWindowManager.getInstance(project)
                    val state = org.jetbrains.plugins.terminal.TerminalTabState()
                    state.myTabName = tabName
                    state.myWorkingDirectory = config.project.basePath
                    state.myIsUserDefinedTabTitle = true

                    val contentManager = toolWindow.contentManager
                    val terminalWidget = manager.createNewSession(manager.terminalRunner, state, contentManager)
                    val shellWidget = org.jetbrains.plugins.terminal.ShellTerminalWidget.asShellJediTermWidget(terminalWidget)

                    if (shellWidget != null) {
                        // Wait for shell, send debug command, then poll for debug port
                        ApplicationManager.getApplication().executeOnPooledThread {
                            // Wait for shell prompt
                            for (i in 1..40) {
                                Thread.sleep(250)
                                try {
                                    if (shellWidget.processTtyConnector != null) break
                                } catch (_: Exception) {}
                            }
                            Thread.sleep(500)

                            // Send the debug command
                            ApplicationManager.getApplication().invokeLater {
                                try {
                                    shellWidget.executeWithTtyConnector { tty ->
                                        tty.write((shellCmd + "\n").toByteArray())
                                    }
                                } catch (_: Exception) {}
                            }

                            // Poll for debug port to open, then attach
                            log.debug { "Waiting for debug port $port..." }
                            for (i in 1..60) { // up to 30 seconds
                                Thread.sleep(500)
                                if (isPortOpen(port)) {
                                    log.debug { "Debug port $port is open, attaching debugger" }
                                    ApplicationManager.getApplication().invokeLater {
                                        attachRemoteDebugger(config, port)
                                    }
                                    break
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Terminal debug failed, falling back", e)
            return JBangDebugRunState(config, environment).execute(executor, runner)
        }

        return null
    }

    private fun isPortOpen(port: Int): Boolean {
        return try {
            Socket("localhost", port).use { true }
        } catch (_: Exception) {
            false
        }
    }

    private fun attachRemoteDebugger(config: JBangRunConfiguration, port: Int) {
        try {
            val project = config.project
            val remoteType = RemoteConfigurationType.getInstance()
            val remoteFactory = remoteType.configurationFactories[0]
            val remoteSettings = RunManager.getInstance(project)
                .createConfiguration("jbang debug ${File(config.scriptPath).name}", remoteFactory)
            val remoteConfig = remoteSettings.configuration as RemoteConfiguration
            remoteConfig.HOST = "localhost"
            remoteConfig.PORT = port.toString()
            remoteConfig.SERVER_MODE = false

            val debugExecutor = DefaultDebugExecutor.getDebugExecutorInstance()
            val debugEnv = ExecutionEnvironmentBuilder
                .create(project, debugExecutor, remoteConfig)
                .build()

            ProgramRunnerUtil.executeConfiguration(debugEnv, false, true)
            log.debug { "Remote debugger attached to localhost:$port" }
        } catch (e: Exception) {
            log.warn("Failed to attach debugger: ${e.message}")
        }
    }
}
