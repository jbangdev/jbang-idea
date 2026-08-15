package dev.jbang.idea.project

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
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
        internal fun save(file: com.intellij.openapi.vfs.VirtualFile) {
            FileDocumentManager.getInstance().getDocument(file)?.let(FileDocumentManager.getInstance()::saveDocument)
        }

        internal fun sync(project: Project, selectedPath: String? = null) {
            val service = JBangProjectService.getInstance(project)
            val path = selectedPath ?: service.activeRootPath ?: return
            val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return
            save(file)
            CoroutineScope(Dispatchers.IO).launch {
                val info = service.resolve(file, deferStatusCompletion = true)
                val errors = info?.resolutionErrors.orEmpty().ifEmpty {
                    if (info != null) emptyList() else listOf("jbang info tools failed; see idea.log for command output")
                }
                val succeeded = info != null && errors.isEmpty()
                fireLibraryChange(project) {
                    service.syncFinished(path, succeeded, errors)
                    val group = NotificationGroupManager.getInstance().getNotificationGroup("JBang")
                    if (succeeded) group.createNotification("Synced ${file.name}", NotificationType.INFORMATION).notify(project)
                    else group.createNotification(
                        "Failed to sync ${file.name}",
                        errors.joinToString("<br>") { StringUtil.escapeXmlEntities(it) },
                        NotificationType.ERROR,
                    ).notify(project)
                }
            }
        }
    }
}
