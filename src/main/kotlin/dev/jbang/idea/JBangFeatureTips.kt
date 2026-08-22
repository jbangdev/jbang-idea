package dev.jbang.idea

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.GotItTooltip
import java.net.URI
import javax.swing.JComponent

object JBangFeatureTips {
    private const val STATUS_ID = "jbang.features.status"
    private const val RUN_ID = "jbang.features.run"
    private const val DEPENDENCIES_ID = "jbang.features.dependencies"
    private val FEATURES_URL = URI("https://github.com/jbangdev/jbang-idea/blob/main/docs/modules/ROOT/pages/features.adoc").toURL()

    private val statusScheduled = Key.create<Boolean>("$STATUS_ID.scheduled")
    private val runScheduled = Key.create<Boolean>("$RUN_ID.scheduled")
    private val dependenciesScheduled = Key.create<Boolean>("$DEPENDENCIES_ID.scheduled")

    internal fun createStatus(parent: Disposable) = GotItTooltip(
        STATUS_ID,
        "Switch active scripts and synchronize their classpath from the JBang status widget.",
        parent,
    ).withHeader("Multiple JBang roots").withBrowserLink("View all features", FEATURES_URL)

    internal fun createRun(parent: Disposable) = GotItTooltip(
        RUN_ID,
        "Use the gutter icon to run or debug this script with JBang.",
        parent,
    ).withHeader("Run and Debug").withBrowserLink("View all features", FEATURES_URL)

    internal fun createDependencies(parent: Disposable) = GotItTooltip(
        DEPENDENCIES_ID,
        "Complete local and remote Maven coordinates and see dependency errors directly in the editor.",
        parent,
    ).withHeader("JBang dependency completion").withBrowserLink("View all features", FEATURES_URL)

    fun showStatus(project: Project) = schedule(
        project,
        statusScheduled,
        { WindowManager.getInstance().getStatusBar(project)?.component },
        ::createStatus,
    ) { tip, component ->
        tip.withPosition(Balloon.Position.above).show(component, GotItTooltip.TOP_MIDDLE)
    }

    fun showRun(project: Project) = schedule(
        project,
        runScheduled,
        { FileEditorManager.getInstance(project).selectedTextEditor?.gutter as? JComponent },
        ::createRun,
    ) { tip, component ->
        tip.withPosition(Balloon.Position.atRight).show(component, GotItTooltip.RIGHT_MIDDLE)
    }

    fun showDependencies(project: Project, editor: Editor) = schedule(
        project,
        dependenciesScheduled,
        { editor.contentComponent },
        ::createDependencies,
    ) { tip, component ->
        tip.withPosition(Balloon.Position.below).show(component, GotItTooltip.TOP_MIDDLE)
    }

    private fun schedule(
        project: Project,
        key: Key<Boolean>,
        component: () -> JComponent?,
        create: (Disposable) -> GotItTooltip,
        show: (GotItTooltip, JComponent) -> Unit,
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || project.getUserData(key) == true) return@invokeLater
            val target = component()?.takeIf(JComponent::isShowing) ?: return@invokeLater
            project.putUserData(key, true)
            create(project).takeIf(GotItTooltip::canShow)?.let { show(it, target) }
        }
    }
}
