package dev.jbang.idea

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import dev.jbang.idea.cli.JBangCli
import dev.jbang.idea.cli.TemplateInfo
import java.io.File

class JBangCreateScriptAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selected = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val directory = if (selected.isDirectory) selected else selected.parent
        object : Task.Backgroundable(project, "Loading JBang templates", true) {
            private var templates = emptyList<TemplateInfo>()
            override fun run(indicator: ProgressIndicator) { templates = JBangCli.listTemplates() }
            override fun onSuccess() {
                if (templates.isEmpty()) {
                    Messages.showErrorDialog(project, "JBang returned no templates.", "New JBang Script")
                    return
                }
                JBPopupFactory.getInstance().createPopupChooserBuilder(templates.map(TemplateInfo::name))
                    .setTitle("JBang Template")
                    .setItemChosenCallback { template ->
                        val name = Messages.showInputDialog(
                            project,
                            "File name:",
                            "New JBang Script",
                            JBangPlugin.icon16,
                            suggestFileName(template),
                            null,
                        )
                            ?.takeIf(String::isNotBlank) ?: return@setItemChosenCallback
                        create(project, directory, name, template)
                    }
                    .createPopup()
                    .showCenteredInCurrentWindow(project)
            }
        }.queue()
    }

    private fun create(project: com.intellij.openapi.project.Project, directory: VirtualFile, name: String, template: String) {
        object : Task.Backgroundable(project, "Creating JBang script", true) {
            private var error: Exception? = null
            override fun run(indicator: ProgressIndicator) {
                try {
                    JBangCli.initScript(template, File(directory.path, name).path)
                } catch (e: Exception) {
                    error = e
                }
            }
            override fun onSuccess() {
                error?.let {
                    Messages.showErrorDialog(project, it.message ?: "JBang init failed", "New JBang Script")
                    return
                }
                directory.refresh(false, false)
                directory.findChild(name)?.let { FileEditorManager.getInstance(project).openFile(it, true) }
            }
        }.queue()
    }

    companion object {
        internal fun suggestFileName(template: String): String =
            template.takeIf { name -> listOf(".java", ".kt", ".groovy", ".jsh", ".md").any(name::endsWith) }
                ?: "$template.java"
    }
}
