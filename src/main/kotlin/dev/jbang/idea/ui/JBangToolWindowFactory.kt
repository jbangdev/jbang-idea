package dev.jbang.idea.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiManager
import com.intellij.ui.content.ContentFactory
import dev.jbang.idea.JBangCli.resolveScriptInfo
import dev.jbang.idea.isJBangScript
import dev.jbang.idea.isJBangScriptFile
import javax.swing.SwingConstants


class JBangToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val jBangToolWindowPanel = JBangToolWindowPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(jBangToolWindowPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class JBangToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(true) {
    private val usagePanel = UsagePanel("JBang Usage:\n")
    private val jbangToolWindow = JBangToolWindow()
    var currentScriptFile: VirtualFile? = null
    var mode: String = "help"

    init {
        createToolbar()
        setContent(usagePanel)
    }


    private fun createToolbar() {
        val actionManager = ActionManager.getInstance()
        val actionGroup = ActionManager.getInstance().getAction("JBang.Toolbar") as ActionGroup
        val actionToolbar = actionManager.createActionToolbar("ACTION_TOOLBAR", actionGroup, true)
        actionToolbar.setOrientation(SwingConstants.HORIZONTAL)
        toolbar = actionToolbar.component
    }

    fun refreshScriptInfo(project: Project, scriptFile: VirtualFile) {
        if (isJBangScriptFile(scriptFile.name)) {
            val scriptPsiFile = PsiManager.getInstance(project).findFile(scriptFile)
            if (scriptPsiFile != null && isJBangScript(scriptPsiFile.text)) {
                refreshJBangScript(project, scriptFile)
            }
        }
    }

    fun refreshJBangScript(project: Project, scriptFile: VirtualFile) {
        if (this.mode == "help") {
            switchToScriptInfoPanel()
        }
        currentScriptFile = scriptFile
        try {
            resolveScriptInfo(scriptFile.path).let {
                jbangToolWindow.update(it)
            }
        } catch (e: Exception) {
            val jbangNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("JBang Failure")
            jbangNotificationGroup.createNotification(
                "Failed to resolve DEPS",
                e.message ?: "Failed to resolve dependencies for ${scriptFile.name}",
                NotificationType.ERROR
            ).notify(project)
        }
    }

    fun switchToHelp() {
        setContent(usagePanel)
        this.mode = "help"
    }

    private fun switchToScriptInfoPanel() {
        setContent(jbangToolWindow.content)
        this.mode = "script"
    }
}
