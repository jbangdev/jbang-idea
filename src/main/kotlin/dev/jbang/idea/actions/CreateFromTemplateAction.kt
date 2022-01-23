package dev.jbang.idea.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.LocalFileSystem
import dev.jbang.idea.JBangCli.generateScriptFrommTemplate
import dev.jbang.idea.JBangCli.listJBangTemplates
import org.jetbrains.kotlin.idea.caches.project.NotUnderContentRootModuleInfo.project

class CreateFromTemplateAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        try {
            val templates = listJBangTemplates()
            val dialogWrapper = JBangTemplatesDialogWrapper(templates)
            if (dialogWrapper.showAndGet()) {
                val scriptName = dialogWrapper.getScriptFileName()
                val templateName = dialogWrapper.getTemplateName()
                if (scriptName.isNotEmpty()) {
                    ApplicationManager.getApplication().runWriteAction {
                        val project = e.getData(CommonDataKeys.PROJECT)!!
                        val directory = e.getData(CommonDataKeys.VIRTUAL_FILE)
                        try {
                            val destDir = directory?.path ?: project.basePath!!
                            generateScriptFrommTemplate(templateName, scriptName, destDir)
                            LocalFileSystem.getInstance().refresh(true)
                        } catch (e: Exception) {
                            val errorText = "Failed to create script from template, please check template and script name!"
                            val jbangNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("JBang Failure")
                            jbangNotificationGroup.createNotification("Failed to resolve DEPS", errorText, NotificationType.ERROR).notify(project)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val errorText = e.message!!
            val jbangNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("JBang Failure")
            jbangNotificationGroup.createNotification("Failed to get JBang templates", errorText, NotificationType.ERROR).notify(project)
        }
    }
}