package dev.jbang.idea.project

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.jbang.idea.settings.JBangSettings
import org.junit.Test

class JBangEditorListenerTest : LightJavaCodeInsightFixtureTestCase() {

    private var previousAutoSync = true

    override fun setUp() {
        super.setUp()
        previousAutoSync = JBangSettings.instance.autoSync
        JBangSettings.instance.autoSync = false
        JBangProjectService.getInstance(project).clear()
    }

    override fun tearDown() {
        try {
            JBangProjectService.getInstance(project).clear()
            JBangSettings.instance.autoSync = previousAutoSync
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testOpeningRootWithAutoSyncOffSwitchesActiveRootWithoutResolving() {
        // Regression: JBangEditorListener shelled out to `jbang info tools` on open even
        // with auto-sync off. On Windows that background resolve raced with and clobbered
        // cached ScriptInfo, flaking CI (e.g. JBangDirectiveAnnotatorTest). With auto-sync
        // off, opening a root must only switch the active root — synchronously, no resolve.
        val file = myFixture.addFileToProject(
            "Root.java",
            "//DEPS com.google.guava:guava:33.0-jre\nclass Root {}",
        )
        val service = JBangProjectService.getInstance(project)
        assertNull("precondition: no cached info yet", service.getInfo(file.virtualFile.path))

        JBangEditorListener(project).fileOpened(FileEditorManager.getInstance(project), file.virtualFile)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(
            "active root must be switched synchronously on open",
            file.virtualFile.path,
            service.activeRootPath,
        )
        assertNull(
            "auto-sync off must not trigger a resolve on open",
            service.getInfo(file.virtualFile.path),
        )
    }
}
