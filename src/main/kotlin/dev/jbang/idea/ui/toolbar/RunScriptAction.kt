package dev.jbang.idea.ui.toolbar

import com.intellij.execution.Executor
import com.intellij.execution.RunManagerEx
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.jbang.idea.run.JBangConfigurationType
import dev.jbang.idea.run.JBangRunConfiguration

class RunScriptAction : BaseToolbarAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project!!
        val jBangToolWindowPanel = getJBangToolWindowPanel(project)
        if (jBangToolWindowPanel?.currentScriptFile != null) {
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