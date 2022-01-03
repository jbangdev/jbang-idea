package dev.jbang.idea.ui.toolbar

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager

class ReloadNowAction : BaseToolbarAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project!!
        val jBangToolWindowPanel = getJBangToolWindowPanel(project)
        if (jBangToolWindowPanel != null) {
            val fileEditorManager = FileEditorManager.getInstance(project)
            if (fileEditorManager.selectedFiles.isNotEmpty()) {
                jBangToolWindowPanel.refreshScriptInfo(fileEditorManager.selectedFiles[0])
            }
        }
    }
}