package dev.jbang.idea

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.GotItTooltip
import javax.swing.JPanel

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

    fun testFeatureTipsUseStableIds() {
        val parent = Disposer.newDisposable(testRootDisposable, "JBang feature tips")

        assertEquals("jbang.features.status", JBangFeatureTips.createStatus(parent).id)
        assertEquals("jbang.features.run", JBangFeatureTips.createRun(parent).id)
        assertEquals("jbang.features.dependencies", JBangFeatureTips.createDependencies(parent).id)
    }
}
