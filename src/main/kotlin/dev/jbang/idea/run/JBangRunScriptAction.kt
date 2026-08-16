package dev.jbang.idea.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import dev.jbang.idea.JBangPlugin
import dev.jbang.idea.project.JBangScriptDetector

/**
 * Right-click editor/project-view action: "Run with JBang" / "Debug with JBang".
 * Works regardless of whether the file is inside a source root.
 */
class JBangRunScriptAction : AnAction("Run with JBang", "Run this script using jbang", JBangPlugin.icon16), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        if (file == null || !JBangScriptDetector.isRootScript(file)) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.project ?: return

        val runManager = RunManager.getInstance(project)
        val factory = JBangConfigurationFactory(JBangConfigurationType())

        // Reuse existing config or create new one
        val configName = "jbang ${file.name}"
        val existing = runManager.findConfigurationByName(configName)
        val settings = if (existing != null) {
            existing
        } else {
            val newSettings = runManager.createConfiguration(configName, factory)
            val config = newSettings.configuration as JBangRunConfiguration
            config.scriptPath = file.path
            runManager.addConfiguration(newSettings)
            newSettings
        }

        runManager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}

class JBangDebugScriptAction : AnAction("Debug with JBang", "Debug this script using jbang", JBangPlugin.icon16), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        if (file == null || !JBangScriptDetector.isRootScript(file)) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.project ?: return

        val runManager = RunManager.getInstance(project)
        val factory = JBangConfigurationFactory(JBangConfigurationType())

        val configName = "jbang ${file.name}"
        val existing = runManager.findConfigurationByName(configName)
        val settings = if (existing != null) {
            existing
        } else {
            val newSettings = runManager.createConfiguration(configName, factory)
            val config = newSettings.configuration as JBangRunConfiguration
            config.scriptPath = file.path
            runManager.addConfiguration(newSettings)
            newSettings
        }

        runManager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
    }
}
