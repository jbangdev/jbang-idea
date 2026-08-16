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
 * Debug in Terminal: launches `jbang run --debug=<port>` in the Terminal,
 * polls for the port, and auto-attaches IntelliJ's remote debugger.
 */
class JBangTerminalDebugRunState(
    private val config: JBangRunConfiguration,
    private val environment: ExecutionEnvironment
) : RunProfileState {

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
        val port = JBangDebugRunState.DEFAULT_DEBUG_PORT
        val shellCmd = JBangDebugRunState.buildDebugShellCommand(config, port)
        val tabName = "jbang debug ${File(config.scriptPath).name}"

        if (!TerminalHelper.runInTerminal(config.project, tabName, shellCmd) { shell ->
                // After command is sent, poll for debug port in background
                ApplicationManager.getApplication().executeOnPooledThread {
                    log.debug { "Waiting for debug port $port..." }
                    for (i in 1..60) {
                        Thread.sleep(500)
                        if (try { Socket("localhost", port).use { true } } catch (_: Exception) { false }) {
                            log.debug { "Debug port $port is open, attaching debugger" }
                            ApplicationManager.getApplication().invokeLater { attachDebugger(port) }
                            break
                        }
                    }
                }
            }) {
            // Terminal not available — fall back to PTY debug
            return JBangDebugRunState(config, environment).execute(executor, runner)
        }
        return null
    }

    private fun attachDebugger(port: Int) {
        try {
            val project = config.project
            val remoteFactory = RemoteConfigurationType.getInstance().configurationFactories[0]
            val settings = RunManager.getInstance(project)
                .createConfiguration("jbang debug ${File(config.scriptPath).name}", remoteFactory)
            (settings.configuration as RemoteConfiguration).apply {
                HOST = "localhost"
                PORT = port.toString()
                SERVER_MODE = false
            }
            val env = ExecutionEnvironmentBuilder
                .create(project, DefaultDebugExecutor.getDebugExecutorInstance(), settings.configuration)
                .build()
            ProgramRunnerUtil.executeConfiguration(env, false, true)
        } catch (e: Exception) {
            log.warn("Failed to attach debugger: ${e.message}")
        }
    }
}
