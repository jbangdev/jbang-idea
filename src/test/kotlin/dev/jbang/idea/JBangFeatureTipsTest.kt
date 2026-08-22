package dev.jbang.idea

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.GotItTooltip
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class JBangFeatureTipsTest : BasePlatformTestCase() {

    fun testTipIsNotMarkedScheduledUntilItsBalloonIsCreated() {
        val key = Key.create<Boolean>("jbang.features.test.scheduled")
        val visibleComponent = object : JPanel() {
            override fun isShowing() = true
        }

        JBangFeatureTips.schedule(
            project,
            key,
            { visibleComponent },
            { GotItTooltip("jbang.features.test", "Test", it) },
        ) { _, _ -> /* Simulate another GotItTooltip preventing creation. */ }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertNull(project.getUserData(key))
    }

    fun testScheduleLetsGotItTooltipHandleDisplayAvailability() {
        val key = Key.create<Boolean>("jbang.features.unavailable.scheduled")
        val visibleComponent = object : JPanel() {
            override fun isShowing() = true
        }
        var showAttempts = 0

        val unavailable = GotItTooltip("jbang.features.unavailable", "Test", testRootDisposable)
        unavailable.gotIt()
        assertFalse(unavailable.canShow())

        JBangFeatureTips.schedule(
            project,
            key,
            { visibleComponent },
            { unavailable },
        ) { _, _ -> showAttempts++ }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(1, showAttempts)
        assertNull(project.getUserData(key))
    }

    fun testRunTipFindsJBangMarkerWithoutEvaluatingTooltip() {
        val marker = object : GutterIconRenderer() {
            override fun getIcon(): Icon = JBangPlugin.icon16
            override fun getTooltipText(): String = error("Tooltip evaluation may access indexes")
            override fun equals(other: Any?) = this === other
            override fun hashCode() = System.identityHashCode(this)
        }
        val bounds = Rectangle(10, 20, 16, 16)

        assertEquals(
            java.awt.Point(18, 28),
            JBangFeatureTips.runMarkerPoint(listOf(marker to bounds)),
        )
    }

    fun testClasspathTipPointsAtVisibleJBangLibraryEntry() {
        val root = DefaultMutableTreeNode("Project")
        val libraries = DefaultMutableTreeNode("External Libraries")
        libraries.add(DefaultMutableTreeNode("jbang: Root.java"))
        root.add(libraries)
        val tree = JTree(root).apply {
            expandRow(0)
            expandRow(1)
            setSize(300, 300)
            doLayout()
        }
        val bounds = tree.getRowBounds(2)

        assertEquals(
            java.awt.Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2),
            JBangFeatureTips.classpathPoint(tree),
        )
    }

    fun testFeatureTipsUseStableIds() {
        val parent = Disposer.newDisposable(testRootDisposable, "JBang feature tips")

        assertEquals("jbang.features.status", JBangFeatureTips.createStatus(parent).id)
        assertEquals("jbang.features.run", JBangFeatureTips.createRun(parent).id)
        assertEquals("jbang.features.dependencies", JBangFeatureTips.createDependencies(parent).id)
        assertEquals("jbang.features.classpath", JBangFeatureTips.createClasspath(parent).id)
    }
}
