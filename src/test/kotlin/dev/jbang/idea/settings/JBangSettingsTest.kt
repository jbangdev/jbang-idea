package dev.jbang.idea.settings

import junit.framework.TestCase

class JBangSettingsTest : TestCase() {

    fun testAccessorsUseLoadedState() {
        val settings = JBangSettings()
        settings.jbangPath = "/old/jbang"

        settings.loadState(JBangSettings.State(jbangPath = "/loaded/jbang", autoSync = false))

        assertEquals("/loaded/jbang", settings.jbangPath)
        assertFalse(settings.autoSync)

        settings.jbangPath = "/changed/jbang"
        settings.autoSync = true

        assertEquals("/changed/jbang", settings.state.jbangPath)
        assertTrue(settings.state.autoSync)
    }
}
