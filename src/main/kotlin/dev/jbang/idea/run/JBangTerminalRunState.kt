package dev.jbang.idea.run

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import dev.jbang.idea.debug
import dev.jbang.idea.jbangLog
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val log = jbangLog<JBangTerminalRunState>()

class JBangTerminalRunState(
    private val config: JBangRunConfiguration,
    private val environment: ExecutionEnvironment
) : RunProfileState {

    private val tabs = config.project.getUserData(TABS_KEY) ?: ConcurrentHashMap<String, ShellTerminalWidget>().also {
        config.project.putUserData(TABS_KEY, it)
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
        val project = config.project
        val shellCmd = JBangRunState.buildShellCommand(config)
        val tabName = "jbang ${File(config.scriptPath).name}"
        val scriptName = File(config.scriptPath).name

        try {
            val manager = TerminalToolWindowManager.getInstance(project)
            val existing = tabs[tabName]

            ApplicationManager.getApplication().executeOnPooledThread {
                var busy = false
                if (existing != null) {
                    try {
                        busy = existing.hasRunningCommands()
                    } catch (_: Exception) {
                        tabs.remove(tabName)
                    }
                }
                val alive = existing != null && tabs.containsKey(tabName)

                ApplicationManager.getApplication().invokeLater {
                    try {
                        if (alive && busy) {
                            log.debug { "Terminal tab '$tabName' is busy, asking user" }
                            handleBusy(manager, existing!!, tabName, shellCmd, scriptName)
                        } else if (alive) {
                            log.debug { "Reusing terminal tab '$tabName'" }
                            focusAndType(existing!!, shellCmd)
                        } else {
                            log.debug { "Creating new terminal tab '$tabName'" }
                            createAndRun(manager, tabName, shellCmd)
                        }
                    } catch (e: Exception) {
                        log.warn("Terminal run failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Terminal plugin unavailable, falling back to PTY", e)
            return JBangRunState(config, environment).execute(executor, runner)
        }

        return null
    }

    private fun handleBusy(
        manager: TerminalToolWindowManager,
        widget: ShellTerminalWidget,
        tabName: String,
        shellCmd: String,
        scriptName: String
    ) {
        val choice = Messages.showYesNoCancelDialog(
            config.project,
            "The terminal for '$scriptName' is still running.\nKill it and rerun, or open a new tab?",
            "JBang",
            "Kill && Rerun",
            "New Tab",
            "Cancel",
            Messages.getQuestionIcon()
        )
        when (choice) {
            Messages.YES -> {
                focusWidget(widget)
                // Ctrl+C via tty, wait, then type new command
                typeToTty(widget, "\u0003") // Ctrl+C
                ApplicationManager.getApplication().executeOnPooledThread {
                    Thread.sleep(500)
                    ApplicationManager.getApplication().invokeLater {
                        typeToTty(widget, shellCmd + "\n")
                    }
                }
            }
            Messages.NO -> {
                // Create new tab but register it under the canonical name
                // so next run finds this one (the latest), not the old one
                val displayName = "$tabName (${System.currentTimeMillis() % 10000})"
                createAndRun(manager, displayName, shellCmd, canonicalName = tabName)
            }
        }
    }

    private fun focusWidget(widget: ShellTerminalWidget) {
        // Activate the Terminal tool window and select the right tab
        try {
            val manager = TerminalToolWindowManager.getInstance(config.project)
            val toolWindow = manager.toolWindow
            toolWindow?.show()
            // Find the content tab containing this widget and select it
            val contentManager = toolWindow?.contentManager
            if (contentManager != null) {
                for (content in contentManager.contents) {
                    if (content.component.isAncestorOf(widget) ||
                        content.component == widget.component) {
                        contentManager.setSelectedContent(content, true)
                        break
                    }
                }
            }
        } catch (_: Exception) {}
        widget.requestFocus()
    }

    private fun focusAndType(widget: ShellTerminalWidget, shellCmd: String) {
        focusWidget(widget)
        typeToTty(widget, shellCmd + "\n")
    }

    /**
     * Write directly to the tty connector. Avoids the first-character-eaten
     * bug in ShellTerminalWidget.executeCommand().
     */
    private fun typeToTty(widget: ShellTerminalWidget, text: String) {
        try {
            widget.executeWithTtyConnector { tty ->
                tty.write(text.toByteArray())
            }
        } catch (e: Exception) {
            log.warn("Failed to write to terminal tty", e)
        }
    }

    private fun createAndRun(manager: TerminalToolWindowManager, tabName: String, shellCmd: String, canonicalName: String? = null) {
        val state = TerminalTabState()
        state.myTabName = tabName
        state.myWorkingDirectory = config.project.basePath
        state.myIsUserDefinedTabTitle = true

        // Ensure the Terminal tool window is open and visible
        val twm = com.intellij.openapi.wm.ToolWindowManager.getInstance(config.project)
        val toolWindow = twm.getToolWindow("Terminal")
        if (toolWindow == null) {
            log.warn("Terminal tool window not found — is the Terminal plugin installed?")
            return
        }
        toolWindow.activate(null)
        val contentManager = toolWindow.contentManager
        val terminalWidget = manager.createNewSession(manager.terminalRunner, state, contentManager)

        val shellWidget = ShellTerminalWidget.asShellJediTermWidget(terminalWidget)
        if (shellWidget != null) {
            tabs[canonicalName ?: tabName] = shellWidget
            // Wait for shell prompt, then type command via tty
            ApplicationManager.getApplication().executeOnPooledThread {
                for (i in 1..40) {
                    Thread.sleep(250)
                    try {
                        if (shellWidget.processTtyConnector != null) break
                    } catch (_: Exception) {}
                }
                Thread.sleep(500)
                ApplicationManager.getApplication().invokeLater {
                    typeToTty(shellWidget, shellCmd + "\n")
                }
            }
        }
    }

    companion object {
        private val TABS_KEY = Key.create<ConcurrentHashMap<String, ShellTerminalWidget>>("jbang.terminal.tabs")
    }
}
