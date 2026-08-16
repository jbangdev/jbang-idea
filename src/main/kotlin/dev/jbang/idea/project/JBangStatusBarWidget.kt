package dev.jbang.idea.project

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import dev.jbang.idea.JBangPlugin
import dev.jbang.idea.settings.JBangSettings
import java.io.File

/**
 * Status bar widget showing the active jbang root script.
 * Click to switch between detected roots.
 */
class JBangStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "JBangActiveRoot"
    override fun getDisplayName(): String = "JBang Active Script"
    override fun createWidget(project: Project): StatusBarWidget = JBangStatusWidget(project)
    override fun isAvailable(project: Project): Boolean = true
}

internal class JBangStatusWidget(
    project: Project,
    private val confirmOpen: (Project, String, DoNotAskOption) -> Boolean = { dialogProject, rootName, doNotAsk ->
        MessageDialogBuilder.yesNo("JBang Root Selected", "Open $rootName in the editor?")
            .yesText("Open")
            .noText("Keep Current File")
            .doNotAsk(doNotAsk)
            .ask(dialogProject)
    },
) : EditorBasedWidget(project), StatusBarWidget.MultipleTextValuesPresentation {

    override fun ID(): String = "JBangActiveRoot"

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getTooltipText(): String {
        val service = JBangProjectService.getInstance(project)
        return when {
            service.syncingRootPath != null -> "Updating JBang classpath and JDK"
            service.lastFailedRootPath != null -> "<html>Last JBang sync failed:<br>" +
                service.lastSyncErrors.joinToString("<br>") { StringUtil.escapeXmlEntities(it) } + "</html>"
            service.lastSucceededRootPath != null -> "Last JBang sync completed"
            else -> "Active JBang script (click to switch or sync)"
        }
    }

    override fun getSelectedValue(): String? {
        val service = JBangProjectService.getInstance(project)
        service.syncingRootPath?.let { return "jbang: syncing ${File(it).name}…" }
        val active = service.activeRootPath ?: return null
        val result = when (active) {
            service.lastFailedRootPath -> " (sync failed: ${service.lastSyncErrorCount} ${if (service.lastSyncErrorCount == 1) "error" else "errors"})"
            service.lastSucceededRootPath -> " (synced)"
            else -> ""
        }
        return "jbang: ${File(active).name}$result"
    }

    override fun getIcon() = JBangPlugin.icon16.takeIf {
        JBangProjectService.getInstance(project).allRoots.isNotEmpty()
    }

    override fun getPopup(): ListPopup? {
        val service = JBangProjectService.getInstance(project)
        val roots = service.allRoots.keys.toList()
        if (roots.isEmpty()) return null
        val sync = "\u0000sync"
        val step = object : BaseListPopupStep<String>("JBang", roots + sync) {
            override fun getTextFor(value: String) = if (value == sync) "Sync now" else File(value).name

            override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                if (selectedValue == sync) JBangSyncAction.sync(project)
                else selectRoot(selectedValue)
                myStatusBar?.updateWidget(ID())
                return FINAL_CHOICE
            }
        }
        return JBPopupFactory.getInstance().createListPopup(step)
    }

    internal fun selectRoot(path: String) {
        JBangProjectService.getInstance(project).setActiveRoot(path)
        if (FileEditorManager.getInstance(project).selectedFiles.any { it.path == path }) return

        val settings = JBangSettings.instance
        val shouldOpen = if (settings.askToOpenSelectedRoot) {
            confirmOpen(project, File(path).name, object : DoNotAskOption.Adapter() {
                override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                    if (isSelected) {
                        settings.askToOpenSelectedRoot = false
                        settings.openSelectedRootWithoutAsking = exitCode == Messages.YES
                    }
                }
            })
        } else {
            settings.openSelectedRootWithoutAsking
        }
        if (shouldOpen) {
            LocalFileSystem.getInstance().findFileByPath(path)?.let {
                FileEditorManager.getInstance(project).openFile(it, true)
            }
        }
    }

}
