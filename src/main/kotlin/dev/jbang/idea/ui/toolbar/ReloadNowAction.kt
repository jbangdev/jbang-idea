package dev.jbang.idea.ui.toolbar

import com.intellij.openapi.actionSystem.AnActionEvent

class ReloadNowAction : BaseToolbarAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project!!
        val jBangToolWindowPanel = getJBangToolWindowPanel(project)
        if (jBangToolWindowPanel != null) {
            val currentScriptFile = jBangToolWindowPanel.currentScriptFile
            if (currentScriptFile != null) {
                jBangToolWindowPanel.refreshScriptInfo(project, currentScriptFile)
            }
        }
    }
}