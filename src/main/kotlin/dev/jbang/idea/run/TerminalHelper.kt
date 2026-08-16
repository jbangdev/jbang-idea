package dev.jbang.idea.run

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import dev.jbang.idea.jbangLog
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

private val log = jbangLog<TerminalHelper>()

/** Shared terminal launch logic for run and debug states. */
object TerminalHelper {

    /**
     * Opens a terminal tab, waits for the shell, and types [shellCmd].
     * Calls [afterSend] with the shell widget once the command is sent.
     * Returns false if the Terminal tool window is unavailable.
     */
    fun runInTerminal(
        project: Project,
        tabName: String,
        shellCmd: String,
        afterSend: ((ShellTerminalWidget) -> Unit)? = null,
    ): Boolean {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Terminal") ?: return false

        ApplicationManager.getApplication().invokeLater {
            toolWindow.activate {
                val manager = TerminalToolWindowManager.getInstance(project)
                val state = TerminalTabState().apply {
                    myTabName = tabName
                    myWorkingDirectory = project.basePath
                    myIsUserDefinedTabTitle = true
                }
                val widget = manager.createNewSession(manager.terminalRunner, state, toolWindow.contentManager)
                val shell = ShellTerminalWidget.asShellJediTermWidget(widget) ?: return@activate

                ApplicationManager.getApplication().executeOnPooledThread {
                    waitForShell(shell)
                    ApplicationManager.getApplication().invokeLater {
                        typeToTty(shell, shellCmd + "\n")
                        afterSend?.invoke(shell)
                    }
                }
            }
        }
        return true
    }

    internal fun waitForShell(shell: ShellTerminalWidget) {
        for (i in 1..40) {
            Thread.sleep(250)
            try { if (shell.processTtyConnector != null) return } catch (_: Exception) {}
        }
        Thread.sleep(500)
    }

    internal fun typeToTty(shell: ShellTerminalWidget, text: String) {
        try {
            shell.executeWithTtyConnector { tty -> tty.write(text.toByteArray()) }
        } catch (e: Exception) {
            log.warn("Failed to write to terminal tty", e)
        }
    }
}
