package dev.jbang.idea.run

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowManager
import dev.jbang.idea.jbangLog
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.io.File

private val log = jbangLog<JBangTerminalRunState>()

class JBangTerminalRunState(
    private val config: JBangRunConfiguration,
    private val environment: ExecutionEnvironment
) : RunProfileState {

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
        val project = config.project
        val shellCmd = JBangRunState.buildShellCommand(config)
        val tabName = "jbang: ${compressedPath(project, config.scriptPath)}"

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Terminal")
        if (toolWindow == null) {
            log.warn("Terminal not available, falling back to PTY")
            return JBangRunState(config, environment).execute(executor, runner)
        }

        ApplicationManager.getApplication().invokeLater {
            toolWindow.activate {
                val manager = TerminalToolWindowManager.getInstance(project)
                // Look for an existing tab by name in the content manager
                val existingContent = toolWindow.contentManager.contents
                    .firstOrNull { it.displayName == tabName }
                val existing = if (existingContent != null) {
                    toolWindow.contentManager.setSelectedContent(existingContent, true)
                    // Find the ShellTerminalWidget inside the content's component tree
                    com.intellij.util.ui.UIUtil.findComponentOfType(existingContent.component, ShellTerminalWidget::class.java)
                } else null

                if (existing != null) {
                    // Reuse: type into existing tab
                    ApplicationManager.getApplication().executeOnPooledThread {
                        TerminalHelper.waitForShell(existing)
                        ApplicationManager.getApplication().invokeLater {
                            TerminalHelper.typeToTty(existing, shellCmd + "\n")
                        }
                    }
                } else {
                    // Create new tab via helper
                    TerminalHelper.runInTerminal(project, tabName, shellCmd)
                }
            }
        }
        return null
    }
}
