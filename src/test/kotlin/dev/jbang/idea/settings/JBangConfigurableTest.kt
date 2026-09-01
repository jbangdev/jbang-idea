package dev.jbang.idea.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel

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

    fun testSettingsShowsResolvedJBangPath() {
        val configurable = JBangConfigurable()
        val component = configurable.createComponent()
        configurable.reset()
        val labels = UIUtil.findComponentsOfType(component, JLabel::class.java)
        val resolved = labels.find { it.text?.startsWith("Resolved:") == true || it.text?.startsWith("Not found") == true }

        assertNotNull("Settings should show resolved jbang path", resolved)
    }

    fun testSettingsExposesHelpLinks() {
        val configurable = JBangConfigurable()
        val component = configurable.createComponent()
        val links = UIUtil.findComponentsOfType(component, com.intellij.ui.components.ActionLink::class.java)
            .map { it.text }

        assertTrue("Settings should link to documentation", links.any { it.contains("Documentation") })
        assertTrue("Settings should link to issue reporting", links.any { it.contains("Report") })
    }

    fun testSettingsHasInstallButton() {
        val configurable = JBangConfigurable()
        val component = configurable.createComponent()
        val buttons = UIUtil.findComponentsOfType(component, JButton::class.java)
        val installButton = buttons.find { it.text == "Install JBang…" }

        assertNotNull("Settings should have an Install JBang button", installButton)
    }
}
