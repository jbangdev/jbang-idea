package dev.jbang.idea.settings

import junit.framework.TestCase

class JBangProjectSettingsTest : TestCase() {

    fun testAsDFlagsFormatsAndSkipsBlankNames() {
        val settings = JBangProjectSettings()
        settings.properties = linkedMapOf(
            "miniterm.version" to "1.2.3",
            "miniterm.ffm" to "",   // empty value is kept (jbang reads -Dname=)
            "" to "ignored",        // blank name is dropped
        )

        assertEquals(
            listOf("-Dminiterm.version=1.2.3", "-Dminiterm.ffm="),
            settings.asDFlags(),
        )
    }

    fun testStateRoundTripsProperties() {
        val settings = JBangProjectSettings()
        settings.loadState(
            JBangProjectSettings.State(properties = linkedMapOf("a" to "1", "b" to "2")),
        )

        assertEquals(mapOf("a" to "1", "b" to "2"), settings.properties)
        assertEquals(listOf("-Da=1", "-Db=2"), settings.asDFlags())
    }

    fun testNoPropertiesYieldsNoFlags() {
        assertTrue(JBangProjectSettings().asDFlags().isEmpty())
    }
}
