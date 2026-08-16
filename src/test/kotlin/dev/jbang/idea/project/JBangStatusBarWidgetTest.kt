package dev.jbang.idea.project

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.jbang.idea.cli.ScriptInfo
import dev.jbang.idea.settings.JBangSettings
import java.nio.file.Files

class JBangStatusBarWidgetTest : LightJavaCodeInsightFixtureTestCase() {
    private var previousAsk = true
    private var previousOpen = false
    private var previousAutoSync = true

    override fun setUp() {
        super.setUp()
        previousAsk = JBangSettings.instance.askToOpenSelectedRoot
        previousOpen = JBangSettings.instance.openSelectedRootWithoutAsking
        previousAutoSync = JBangSettings.instance.autoSync
        JBangSettings.instance.askToOpenSelectedRoot = true
        JBangSettings.instance.openSelectedRootWithoutAsking = false
        JBangSettings.instance.autoSync = false
        JBangProjectService.getInstance(project).clear()
    }

    override fun tearDown() {
        JBangProjectService.getInstance(project).clear()
        JBangSettings.instance.askToOpenSelectedRoot = previousAsk
        JBangSettings.instance.openSelectedRootWithoutAsking = previousOpen
        JBangSettings.instance.autoSync = previousAutoSync
        super.tearDown()
    }

    fun testSelectingDifferentRootCanOpenAndRememberChoice() {
        val rootA = myFixture.addFileToProject("RootA.java", "//DEPS example:a:1\nclass RootA {}")
        val rootBPath = Files.writeString(Files.createTempFile("RootB", ".java"), "//DEPS example:b:1\nclass RootB {}")
        VfsRootAccess.allowRootAccess(testRootDisposable, rootBPath.toRealPath().parent.toString())
        val rootB = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(rootBPath)!!
        val service = JBangProjectService.getInstance(project)
        service.cacheResolved(rootA.virtualFile.path, ScriptInfo(), emptyList())
        service.cacheResolved(rootB.path, ScriptInfo(), emptyList())
        service.setActiveRoot(rootA.virtualFile.path)
        FileEditorManager.getInstance(project).openFile(rootA.virtualFile, true)
        var prompts = 0
        val widget = JBangStatusWidget(project) { _, _, doNotAsk ->
            prompts++
            doNotAsk.setToBeShown(false, Messages.YES)
            true
        }

        widget.selectRoot(rootB.path)

        assertEquals(1, prompts)
        assertEquals(rootB, FileEditorManager.getInstance(project).selectedFiles.single())
        assertFalse(JBangSettings.instance.askToOpenSelectedRoot)
        assertTrue(JBangSettings.instance.openSelectedRootWithoutAsking)

        FileEditorManager.getInstance(project).openFile(rootA.virtualFile, true)
        widget.selectRoot(rootB.path)
        assertEquals("Remembered choices should not prompt again", 1, prompts)
        assertEquals(rootB, FileEditorManager.getInstance(project).selectedFiles.single())
        widget.dispose()
    }

    fun testSelectingCurrentFileDoesNotPrompt() {
        val root = myFixture.addFileToProject("Root.java", "//DEPS example:a:1\nclass Root {}")
        val service = JBangProjectService.getInstance(project)
        service.cacheResolved(root.virtualFile.path, ScriptInfo(), emptyList())
        FileEditorManager.getInstance(project).openFile(root.virtualFile, true)
        var prompts = 0
        val widget = JBangStatusWidget(project) { _, _, _ -> prompts++; true }

        widget.selectRoot(root.virtualFile.path)

        assertEquals(0, prompts)
        widget.dispose()
    }
}
