package dev.jbang.idea.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiManager
import dev.jbang.idea.isJBangScript
import dev.jbang.idea.isJBangScriptFile


class JBangToolWindowListener(val project: Project) : ToolWindowManagerListener {

    override fun toolWindowShown(toolWindow: ToolWindow) {
        if (toolWindow.id == "JBang") {
            val content = toolWindow.contentManager.contents.first { it.component is JBangToolWindowPanel }
            val jBangToolWindowPanel = content.component as JBangToolWindowPanel
            val fileEditorManager = FileEditorManager.getInstance(project)
            if (fileEditorManager.selectedFiles.isNotEmpty()) { //refresh script info for current editor
                val currentOpenedFile = fileEditorManager.selectedFiles[0]
                if (isJBangScriptFile(currentOpenedFile.name)) {
                    val scriptPsiFile = PsiManager.getInstance(project).findFile(currentOpenedFile)
                    if (scriptPsiFile != null && isJBangScript(scriptPsiFile.text)) {
                        jBangToolWindowPanel.refreshJBangScript(project, currentOpenedFile)
                        return
                    }
                }
            }
            jBangToolWindowPanel.switchToHelp()
        }
    }
}