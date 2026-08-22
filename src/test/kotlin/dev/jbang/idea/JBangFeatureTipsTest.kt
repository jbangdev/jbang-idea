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

    fun testFeatureTipsUseStableIds() {
        val parent = Disposer.newDisposable(testRootDisposable, "JBang feature tips")

        assertEquals("jbang.features.status", JBangFeatureTips.createStatus(parent).id)
        assertEquals("jbang.features.run", JBangFeatureTips.createRun(parent).id)
        assertEquals("jbang.features.dependencies", JBangFeatureTips.createDependencies(parent).id)
    }
}
