package dev.jbang.idea.ui.toolbar

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.wm.ToolWindowManager
import dev.jbang.idea.ui.JBangToolWindowPanel

class ReloadNowAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project!!
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JBang")
        if (toolWindow != null) {
            val content = toolWindow.contentManager.contents.first { it.component is JBangToolWindowPanel }
            val jBangToolWindowPanel = content.component as JBangToolWindowPanel

            val fileEditorManager = FileEditorManager.getInstance(project)
            if (fileEditorManager.selectedFiles.isNotEmpty()) {
                jBangToolWindowPanel.refreshScriptInfo(fileEditorManager.selectedFiles[0])
            }
        }
    }
}