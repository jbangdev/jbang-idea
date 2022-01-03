package dev.jbang.idea.ui.toolbar

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import dev.jbang.idea.ui.JBangToolWindowPanel

abstract class BaseToolbarAction : AnAction() {

    fun getJBangToolWindowPanel(project: Project): JBangToolWindowPanel? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JBang")
        if (toolWindow != null) {
            val content = toolWindow.contentManager.contents.first { it.component is JBangToolWindowPanel }
            return content.component as JBangToolWindowPanel
        }
        return null
    }
}