package dev.jbang.idea

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.GotItTooltip
import com.intellij.util.ui.UIUtil
import java.awt.Point
import java.awt.Rectangle
import java.net.URI
import javax.swing.JComponent
import javax.swing.JTree

object JBangFeatureTips {
    private val log = jbangLog<JBangFeatureTips>()
    private const val STATUS_ID = "jbang.features.status"
    private const val RUN_ID = "jbang.features.run"
    private const val DEPENDENCIES_ID = "jbang.features.dependencies"
    private const val CLASSPATH_ID = "jbang.features.classpath"
    private val FEATURES_URL = URI("https://github.com/jbangdev/jbang-idea/blob/main/docs/modules/ROOT/pages/features.adoc").toURL()

    private val statusScheduled = Key.create<Boolean>("$STATUS_ID.scheduled")
    private val runScheduled = Key.create<Boolean>("$RUN_ID.scheduled")
    private val dependenciesScheduled = Key.create<Boolean>("$DEPENDENCIES_ID.scheduled")
    private val classpathScheduled = Key.create<Boolean>("$CLASSPATH_ID.scheduled")

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

    internal fun createClasspath(parent: Disposable) = GotItTooltip(
        CLASSPATH_ID,
        "Each JBang root exposes its resolved dependencies as an isolated classpath under External Libraries.",
        parent,
    ).withHeader("JBang classpath").withBrowserLink("View all features", FEATURES_URL)

    fun showStatus(project: Project) = schedule(
        project,
        statusScheduled,
        { WindowManager.getInstance().getStatusBar(project)?.component },
        ::createStatus,
    ) { tip, component ->
        tip.withPosition(Balloon.Position.above).show(component, GotItTooltip.TOP_MIDDLE)
    }

    fun showRun(project: Project, line: Int) = schedule(
        project,
        runScheduled,
        { FileEditorManager.getInstance(project).selectedTextEditor?.gutter as? EditorGutterComponentEx },
        ::createRun,
    ) { tip, component ->
        val gutter = component as EditorGutterComponentEx
        runMarkerPoint(gutter, line)?.let { point ->
            tip.withPosition(Balloon.Position.atRight).show(gutter) { _, _ -> point }
        }
    }

    internal fun runMarkerPoint(gutter: EditorGutterComponentEx, line: Int): Point? =
        runMarkerPoint(gutter.getGutterRenderersAndRectangles(line).map { it.first to it.second })

    internal fun runMarkerPoint(renderers: List<Pair<GutterMark, Rectangle>>): Point? =
        renderers.firstOrNull { it.first.icon == JBangPlugin.icon16 }
            ?.second
            ?.let { Point(it.x + it.width / 2, it.y + it.height / 2) }

    fun showDependencies(project: Project, editor: Editor) = schedule(
        project,
        dependenciesScheduled,
        { editor.contentComponent },
        ::createDependencies,
    ) { tip, component ->
        tip.withPosition(Balloon.Position.below).show(component, GotItTooltip.TOP_MIDDLE)
    }

    fun showClasspath(project: Project) = schedule(
        project,
        classpathScheduled,
        { ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)?.component },
        ::createClasspath,
    ) { tip, component ->
        val tree = UIUtil.findComponentOfType(component, JTree::class.java)
        val point = tree?.let(::classpathPoint)
        if (tree != null && point != null) {
            tip.withPosition(Balloon.Position.atRight).show(tree) { _, _ -> point }
        } else {
            tip.withPosition(Balloon.Position.below).show(component, GotItTooltip.TOP_MIDDLE)
        }
    }

    internal fun classpathPoint(tree: JTree): Point? = sequenceOf("jbang:", "External Libraries")
        .mapNotNull { text ->
            (0 until tree.rowCount).firstOrNull { row ->
                tree.getPathForRow(row)?.lastPathComponent.toString().contains(text, ignoreCase = true)
            }
        }
        .firstOrNull()
        ?.let(tree::getRowBounds)
        ?.let { Point(it.x + it.width / 2, it.y + it.height / 2) }

    internal fun schedule(
        project: Project,
        key: Key<Boolean>,
        component: () -> JComponent?,
        create: (Disposable) -> GotItTooltip,
        show: (GotItTooltip, JComponent) -> Unit,
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || project.getUserData(key) == true) return@invokeLater
            val target = component()?.takeIf(JComponent::isShowing) ?: return@invokeLater
            val tip = create(project)
            tip.setOnBalloonCreated {
                log.debug { "Showing feature tip ${tip.id}" }
                project.putUserData(key, true)
            }
            show(tip, target)
        }
    }
}
