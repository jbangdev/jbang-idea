package dev.jbang.idea

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JBangFeatureTipsTest : BasePlatformTestCase() {

    fun testFeatureTipsUseStableIds() {
        val parent = Disposer.newDisposable(testRootDisposable, "JBang feature tips")

        assertEquals("jbang.features.status", JBangFeatureTips.createStatus(parent).id)
        assertEquals("jbang.features.run", JBangFeatureTips.createRun(parent).id)
        assertEquals("jbang.features.dependencies", JBangFeatureTips.createDependencies(parent).id)
    }
}
