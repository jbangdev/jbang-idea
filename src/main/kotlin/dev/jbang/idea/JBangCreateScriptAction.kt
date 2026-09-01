package dev.jbang.idea

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
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
        if (JBangCli.resolveJBangPath() == null) {
            Messages.showErrorDialog(project, "JBang is not installed. Install it from Settings > Tools > JBang.", "New JBang Script")
            return
        }
        object : Task.Backgroundable(project, "Loading JBang templates", true) {
            private var templates = emptyList<TemplateInfo>()
            override fun run(indicator: ProgressIndicator) { templates = JBangCli.listTemplates() }
            override fun onSuccess() {
                val dialog = JBangCreateScriptDialog(project, templates, directory)
                if (!dialog.showAndGet()) return
                val name = dialog.scriptName
                val template = dialog.selectedTemplate
                create(project, directory, name, template, dialog.propertyOverrides)
            }
        }.queue()
    }

    private fun create(
        project: com.intellij.openapi.project.Project,
        directory: VirtualFile,
        name: String,
        template: String?,
        properties: Map<String, String>,
    ) {
        object : Task.Backgroundable(project, "Creating JBang script", true) {
            private var error: Exception? = null
            private var destination: File? = null
            override fun run(indicator: ProgressIndicator) {
                try {
                    val target = File(directory.path, name).normalize()
                    target.parentFile?.mkdirs()
                    JBangCli.initScript(target.path, template, properties)
                    destination = target
                } catch (e: Exception) {
                    error = e
                }
            }
            override fun onSuccess() {
                error?.let {
                    Messages.showErrorDialog(project, it.message ?: "JBang init failed", "New JBang Script")
                    return
                }
                val created = destination ?: return
                val file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(created)
                if (file == null) {
                    Messages.showErrorDialog(project, "JBang did not create ${created.path}", "New JBang Script")
                    return
                }
                FileEditorManager.getInstance(project).openFile(file, true)
            }
        }.queue()
    }

    companion object {
        internal fun suggestFileName(template: String?): String {
            val templateName = template?.substringBefore('@')
            return when {
                templateName == null -> ""
                listOf(".java", ".kt", ".groovy", ".jsh", ".md").any(templateName::endsWith) -> templateName
                else -> "$templateName.java"
            }
        }
    }
}
