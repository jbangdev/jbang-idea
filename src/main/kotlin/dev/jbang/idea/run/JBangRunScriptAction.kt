package dev.jbang.idea.run

import com.intellij.execution.Executor
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.guessProjectDir
import dev.jbang.idea.JBangPlugin
import dev.jbang.idea.project.JBangScriptDetector

internal fun prepareJBangConfiguration(project: Project, file: VirtualFile): com.intellij.execution.RunnerAndConfigurationSettings {
    val runManager = RunManager.getInstance(project)
    runManager.allSettings.firstOrNull {
        val configuration = it.configuration as? JBangRunConfiguration
        configuration?.scriptPath == file.path
    }?.let { return it }

    val relativePath = project.guessProjectDir()?.let { VfsUtilCore.getRelativePath(file, it) }
    val configName = "jbang ${relativePath ?: file.name}"
    return runManager.createConfiguration(configName, JBangConfigurationFactory(JBangConfigurationType())).also {
        (it.configuration as JBangRunConfiguration).scriptPath = file.path
        runManager.setTemporaryConfiguration(it)
    }
}

private fun runJBang(project: Project, file: VirtualFile, executor: Executor) {
    val runManager = RunManager.getInstance(project)
    val settings = prepareJBangConfiguration(project, file)
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
