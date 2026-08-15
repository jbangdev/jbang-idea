package dev.jbang.idea.run

import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.execution.remote.RemoteConfigurationType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import dev.jbang.idea.cli.JBangCli
import dev.jbang.idea.debug
import dev.jbang.idea.jbangLog

private val log = jbangLog<JBangDebugRunState>()

/**
 * Debug state: launches `jbang run --debug=<port> <script>` and auto-attaches
 * IntelliJ's remote debugger once the JVM debug agent is listening.
 */
class JBangDebugRunState(
    private val config: JBangRunConfiguration,
    private val environment: ExecutionEnvironment
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val cmd = buildDebugCommandLine(config, DEFAULT_DEBUG_PORT)
            .withWorkDirectory(config.project.basePath)

        val handler = KillableColoredProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler)

        // Watch stdout/stderr for the JDWP "Listening" message, then auto-attach
        handler.addProcessListener(object : ProcessAdapter() {
            @Volatile
            private var attached = false

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (attached) return
                val text = event.text
                if (text.contains("Listening for transport") || text.contains("Listening on address")) {
                    attached = true
                    log.debug { "JBang debug agent ready, attaching debugger on port $DEFAULT_DEBUG_PORT" }
                    ApplicationManager.getApplication().invokeLater {
                        attachRemoteDebugger(config, DEFAULT_DEBUG_PORT)
                    }
                }
            }
        })

        return handler
    }

    private fun attachRemoteDebugger(config: JBangRunConfiguration, port: Int) {
        try {
            val project = config.project
            val remoteType = RemoteConfigurationType.getInstance()
            val remoteFactory = remoteType.configurationFactories[0]
            val remoteSettings = com.intellij.execution.RunManager.getInstance(project)
                .createConfiguration("jbang debug ${java.io.File(config.scriptPath).name}", remoteFactory)
            val remoteConfig = remoteSettings.configuration as RemoteConfiguration
            remoteConfig.HOST = "localhost"
            remoteConfig.PORT = port.toString()
            remoteConfig.SERVER_MODE = false // attach mode

            val debugExecutor = DefaultDebugExecutor.getDebugExecutorInstance()
            val debugEnv = ExecutionEnvironmentBuilder
                .create(project, debugExecutor, remoteConfig)
                .build()

            com.intellij.execution.ProgramRunnerUtil.executeConfiguration(debugEnv, false, true)
            log.debug { "Remote debugger attached to localhost:$port" }
        } catch (e: Exception) {
            log.warn("Failed to auto-attach debugger: ${e.message}")
        }
    }

    companion object {
        const val DEFAULT_DEBUG_PORT = 4004

        fun buildDebugCommandLine(config: JBangRunConfiguration, port: Int): GeneralCommandLine {
            val jbang = JBangCli.findJBangCmd()
            val args = mutableListOf(jbang, "run", "--debug=$port", config.scriptPath)
            if (config.scriptArgs.isNotBlank()) {
                args.addAll(config.scriptArgs.split(" "))
            }
            return PtyCommandLine(args)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        }

        fun buildDebugShellCommand(config: JBangRunConfiguration, port: Int): String {
            val jbang = JBangRunState.quote(JBangCli.findJBangCmd())
            val script = JBangRunState.quote(config.scriptPath)
            val parts = mutableListOf(jbang, "run", "--debug=$port", script)
            if (config.scriptArgs.isNotBlank()) {
                parts.add(config.scriptArgs)
            }
            return parts.joinToString(" ")
        }
    }
}
