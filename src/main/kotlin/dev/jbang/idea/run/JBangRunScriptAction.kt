package dev.jbang.idea.run

import com.intellij.execution.Executor
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.jbang.idea.JBangPlugin
import dev.jbang.idea.project.JBangScriptDetector

private fun runJBang(project: Project, file: VirtualFile, executor: Executor) {
    val runManager = RunManager.getInstance(project)
    val factory = JBangConfigurationFactory(JBangConfigurationType())
    val configName = "jbang ${file.name}"
    val settings = runManager.findConfigurationByName(configName) ?: runManager.createConfiguration(configName, factory).also {
        (it.configuration as JBangRunConfiguration).scriptPath = file.path
        runManager.addConfiguration(it)
    }
    runManager.selectedConfiguration = settings
    ProgramRunnerUtil.executeConfiguration(settings, executor)
}

class JBangRunScriptAction : AnAction("Run with JBang", "Run this script using jbang", JBangPlugin.icon16), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.VIRTUAL_FILE)?.let(JBangScriptDetector::isRootScript) == true
    }
    override fun actionPerformed(e: AnActionEvent) {
        runJBang(e.project ?: return, e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return, DefaultRunExecutor.getRunExecutorInstance())
    }
}

class JBangDebugScriptAction : AnAction("Debug with JBang", "Debug this script using jbang", JBangPlugin.icon16), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.VIRTUAL_FILE)?.let(JBangScriptDetector::isRootScript) == true
    }
    override fun actionPerformed(e: AnActionEvent) {
        runJBang(e.project ?: return, e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return, DefaultDebugExecutor.getDebugExecutorInstance())
    }
}
