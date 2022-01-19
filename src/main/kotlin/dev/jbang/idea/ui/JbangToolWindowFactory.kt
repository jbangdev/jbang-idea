package dev.jbang.idea.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.jbang.idea.JBangCli.resolveScriptInfo
import dev.jbang.idea.isJBangScriptFile
import javax.swing.SwingConstants


class JbangToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val jBangToolWindowPanel = JBangToolWindowPanel(project, toolWindow)
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(jBangToolWindowPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class JBangToolWindowPanel(private val project: Project, val toolWindow: ToolWindow) : SimpleToolWindowPanel(true) {
    val usagePanel = UsagePanel("JBang Usage:\n")
    private val jbangToolWindow = JbangToolWindow()
    var currentScriptFile: VirtualFile? = null
        get() = field

    init {
        val fileEditorManager = FileEditorManager.getInstance(project)
        if (fileEditorManager.selectedFiles.isNotEmpty()) {
            refreshScriptInfo(fileEditorManager.selectedFiles[0])
        } else {
            setContent(usagePanel)
        }
        createToolbar()
    }


    private fun createToolbar() {
        val actionManager = ActionManager.getInstance()
        val actionGroup = ActionManager.getInstance().getAction("JBang.Toolbar") as ActionGroup
        val actionToolbar = actionManager.createActionToolbar("ACTION_TOOLBAR", actionGroup, true)
        actionToolbar.setOrientation(SwingConstants.HORIZONTAL)
        toolbar = actionToolbar.component
    }

    fun refreshScriptInfo(scripFile: VirtualFile) {
        if (isJBangScriptFile(scripFile.name)) {
            if (currentScriptFile == null) {
                switchToScriptInfoPanel();
            }
            currentScriptFile = scripFile
            try {
                resolveScriptInfo(scripFile.path).let {
                    jbangToolWindow.update(it)
                }
            } catch (e: Exception) {
                val jbangNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("JBang Failure");
                jbangNotificationGroup.createNotification(
                    "Failed to resolve DEPS",
                    e.message ?: "Failed to resolve dependencies for ${scripFile.name}",
                    NotificationType.ERROR
                ).notify(project)

            }
        }
    }

    private fun switchToScriptInfoPanel() {
        setContent(jbangToolWindow.content)
    }
}
