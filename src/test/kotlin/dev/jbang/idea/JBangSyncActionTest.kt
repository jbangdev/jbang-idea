package dev.jbang.idea

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import dev.jbang.idea.project.JBangSyncAction
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

    fun testSyncActionIsRegistered() {
        val action = ActionManager.getInstance().getAction("jbang.Sync")

        assertNotNull(action)
        assertEquals("Sync JBang Project", action.templatePresentation.text)
    }
}
