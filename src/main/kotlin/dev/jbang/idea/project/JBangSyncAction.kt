package dev.jbang.idea.project

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import dev.jbang.idea.cli.JBangCli
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JBangSyncAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val selected = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = project != null &&
            (selected?.let(JBangScriptDetector::isRootScript) == true || JBangProjectService.getInstance(project).activeRootPath != null)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selected = e.getData(CommonDataKeys.VIRTUAL_FILE)
        sync(project, selected?.takeIf(JBangScriptDetector::isRootScript)?.path)
    }

    companion object {
        private fun notifyJBangNotFound(project: Project) {
            NotificationGroupManager.getInstance().getNotificationGroup("JBang")
                .createNotification(
                    "JBang not found",
                    "Install JBang or configure its path in Settings > Tools > JBang.",
                    NotificationType.WARNING,
                )
                .addAction(object : AnAction("Install JBang\u2026") {
                    override fun actionPerformed(e: AnActionEvent) {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, "JBang")
                    }
                })
                .addAction(object : AnAction("Download Page") {
                    override fun actionPerformed(e: AnActionEvent) {
                        BrowserUtil.browse("https://www.jbang.dev/download")
                    }
                })
                .notify(project)
        }

        internal fun save(file: com.intellij.openapi.vfs.VirtualFile) {
            val documents = FileDocumentManager.getInstance()
            WriteIntentReadAction.run {
                documents.getDocument(file)?.let(documents::saveDocument)
            }
        }

        internal fun sync(project: Project, selectedPath: String? = null) {
            val service = JBangProjectService.getInstance(project)
            val path = selectedPath ?: service.activeRootPath ?: return
            val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return
            save(file)
            if (JBangCli.resolveJBangPath() == null) {
                notifyJBangNotFound(project)
                return
            }
            service.scope.launch {
                val info = service.resolve(file, deferStatusCompletion = true)
                val errors = info?.resolutionErrors.orEmpty().ifEmpty {
                    if (info != null) emptyList() else listOf("jbang info tools failed; see idea.log for command output")
                }
                val succeeded = info != null && errors.isEmpty()
                fireLibraryChange(project) {
                    service.syncFinished(path, succeeded, errors)
                    if (succeeded) {
                        NotificationGroupManager.getInstance().getNotificationGroup("JBang")
                            .createNotification("Synced ${file.name}", NotificationType.INFORMATION)
                            .notify(project)
                    }
                }
            }
        }
    }
}
