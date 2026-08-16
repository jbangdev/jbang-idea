package dev.jbang.idea

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JBangCreateScriptActionTest : BasePlatformTestCase() {

    fun testTemplateSuggestsFileName() {
        assertEquals("readme.md", JBangCreateScriptAction.suggestFileName("readme.md"))
        assertEquals("hello.kt", JBangCreateScriptAction.suggestFileName("hello.kt"))
        assertEquals("qrest.java", JBangCreateScriptAction.suggestFileName("qrest"))
    }

    fun testNoTemplateSuggestsEmptyFileName() {
        assertEquals("", JBangCreateScriptAction.suggestFileName(null))
    }

    fun testOkDisabledWithEmptyName() {
        val dialog = JBangCreateScriptDialog(project, emptyList())
        dialog.nameField.text = ""
        assertFalse("OK should be disabled with empty name", dialog.isOKActionEnabled)
        dialog.nameField.text = "hello.java"
        assertTrue("OK should be enabled with a name", dialog.isOKActionEnabled)
    }

    fun testSelectingTemplateUpdatesName() {
        val templates = listOf(
            dev.jbang.idea.cli.TemplateInfo(name = "hello", description = "Hello World"),
            dev.jbang.idea.cli.TemplateInfo(name = "cli", description = "CLI app"),
            dev.jbang.idea.cli.TemplateInfo(name = "hello.kt", description = "Kotlin Hello"),
        )
        val dialog = JBangCreateScriptDialog(project, templates)

        dialog.templateList.selectedIndex = 0
        assertEquals("hello.java", dialog.nameField.text)

        dialog.templateList.selectedIndex = 2
        assertEquals("hello.kt", dialog.nameField.text)

        // Clearing selection clears the suggested name
        dialog.templateList.clearSelection()
        assertEquals("", dialog.nameField.text)
    }

    fun testNoTemplateSelectedAllowsCustomName() {
        val dialog = JBangCreateScriptDialog(project, emptyList())
        dialog.nameField.text = "myscript.java"
        assertNull("No template selected", dialog.selectedTemplate)
        assertEquals("myscript.java", dialog.scriptName)
        assertTrue(dialog.isOKActionEnabled)
    }

    fun testNewScriptActionIsRegistered() {
        val action = ActionManager.getInstance().getAction("jbang.CreateScript")

        assertNotNull(action)
        assertEquals("JBang Script", action.templatePresentation.text)
    }
}
