package dev.jbang.idea.ui.toolbar

import com.intellij.execution.Executor
import com.intellij.execution.RunManagerEx
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import dev.jbang.idea.run.JBangConfigurationType
import dev.jbang.idea.run.JBangRunConfiguration
import dev.jbang.idea.ui.JBangToolWindowPanel

class RunScriptAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project!!
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JBang")
        if (toolWindow != null) {
            val content = toolWindow.contentManager.contents.first { it.component is JBangToolWindowPanel }
            val jBangToolWindowPanel = content.component as JBangToolWindowPanel
            if (jBangToolWindowPanel.currentScriptFile != null) {
                val scriptFile = jBangToolWindowPanel.currentScriptFile!!
                val projectPath = project.basePath!!
                val scriptPath = scriptFile.path.substring(projectPath.length + 1)
                val runner = RunManagerEx.getInstanceEx(project).createConfiguration("${scriptFile.name} by JBang", JBangConfigurationType::class.java)
                val configuration = runner.configuration as JBangRunConfiguration
                configuration.setScriptName(scriptPath)
                RunManagerEx.getInstanceEx(project).setTemporaryConfiguration(runner)
                ExecutionUtil.runConfiguration(runner, Executor.EXECUTOR_EXTENSION_NAME.extensionList.first())
            }
        }
    }
}