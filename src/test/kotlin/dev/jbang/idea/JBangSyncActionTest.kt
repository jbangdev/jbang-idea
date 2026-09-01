package dev.jbang.idea

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import dev.jbang.idea.project.JBangSyncAction
import kotlinx.coroutines.isActive
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JBangSyncActionTest : BasePlatformTestCase() {
    fun testExplicitSyncSavesRootBeforeInvokingJbang() {
        val file = myFixture.addFileToProject("Root.java", "class Root {}").virtualFile
        val documents = FileDocumentManager.getInstance()
        val document = documents.getDocument(file)!!
        runWriteAction { document.setText("//JAVA 25\nclass Root {}") }
        assertTrue(documents.isDocumentUnsaved(document))

        JBangSyncAction.save(file)

        assertFalse(documents.isDocumentUnsaved(document))
        assertTrue(VfsUtilCore.loadText(file).startsWith("//JAVA 25"))
    }

    fun testExplicitSyncCanSaveFromEdtWithoutAmbientReadAccess() {
        val file = myFixture.addFileToProject("Root.java", "class Root {}").virtualFile
        var error: Throwable? = null

        ApplicationManager.getApplication().invokeLater {
            try {
                JBangSyncAction.save(file)
            } catch (t: Throwable) {
                error = t
            }
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertNull(error)
    }

    fun testProjectServiceScopeIsCancelledOnDispose() {
        val service = dev.jbang.idea.project.JBangProjectService.getInstance(project)
        assertTrue("Service scope must be active while the project is open", service.scope.isActive)

        service.dispose()

        assertFalse("Disposing the project must cancel background sync work", service.scope.isActive)
    }

    fun testSyncActionIsRegistered() {
        val action = ActionManager.getInstance().getAction("jbang.Sync")

        assertNotNull(action)
        assertEquals("Sync JBang Project", action.templatePresentation.text)
    }
}
