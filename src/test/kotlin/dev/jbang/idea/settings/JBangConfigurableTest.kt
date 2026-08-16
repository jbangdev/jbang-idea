package dev.jbang.idea.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import javax.swing.JCheckBox

class JBangConfigurableTest : BasePlatformTestCase() {
    private var previousAsk = true
    private var previousOpen = false

    override fun setUp() {
        super.setUp()
        previousAsk = JBangSettings.instance.askToOpenSelectedRoot
        previousOpen = JBangSettings.instance.openSelectedRootWithoutAsking
        JBangSettings.instance.askToOpenSelectedRoot = false
        JBangSettings.instance.openSelectedRootWithoutAsking = true
    }

    override fun tearDown() {
        JBangSettings.instance.askToOpenSelectedRoot = previousAsk
        JBangSettings.instance.openSelectedRootWithoutAsking = previousOpen
        super.tearDown()
    }

    fun testSettingsCanRestoreRootOpeningPrompt() {
        val configurable = JBangConfigurable()
        val component = configurable.createComponent()
        configurable.reset()
        val checkbox = UIUtil.findComponentsOfType(component, JCheckBox::class.java)
            .single { it.text == "Ask to open a root selected from the status bar" }

        assertFalse(checkbox.isSelected)
        checkbox.isSelected = true
        assertTrue(configurable.isModified)
        configurable.apply()

        assertTrue(JBangSettings.instance.askToOpenSelectedRoot)
        assertFalse(JBangSettings.instance.openSelectedRootWithoutAsking)
    }
}
