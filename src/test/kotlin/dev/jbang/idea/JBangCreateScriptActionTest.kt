package dev.jbang.idea

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JBangCreateScriptActionTest : BasePlatformTestCase() {

    fun testTemplateSuggestsFileName() {
        assertEquals("readme.md", JBangCreateScriptAction.suggestFileName("readme.md"))
        assertEquals("hello.kt", JBangCreateScriptAction.suggestFileName("hello.kt"))
        assertEquals("qrest.java", JBangCreateScriptAction.suggestFileName("qrest"))
    }

    fun testNewScriptActionIsRegistered() {
        val action = ActionManager.getInstance().getAction("jbang.CreateScript")

        assertNotNull(action)
        assertEquals("JBang Script", action.templatePresentation.text)
    }
}
