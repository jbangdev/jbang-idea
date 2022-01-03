package dev.jbang.idea.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
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
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, jBangToolWindowPanel)
    }
}

class JBangToolWindowPanel(private val project: Project, val toolWindow: ToolWindow) : SimpleToolWindowPanel(true), FileEditorManagerListener {
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
            resolveScriptInfo(scripFile.path).let {
                jbangToolWindow.update(it)
            }
        }
    }

    private fun switchToScriptInfoPanel() {
        setContent(jbangToolWindow.content)
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        super.fileOpened(source, file)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        super.fileClosed(source, file)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        super.selectionChanged(event)
        event.newFile?.let { refreshScriptInfo(it) }
    }

}
