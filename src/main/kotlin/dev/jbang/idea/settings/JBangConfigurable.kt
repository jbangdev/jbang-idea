package dev.jbang.idea.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class JBangConfigurable : Configurable {
    private var settingsComponent: JBangSettingsComponent? = null

    override fun getDisplayName(): String {
        return "JBang"
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return settingsComponent!!.getPanel()
    }

    override fun createComponent(): JComponent {
        settingsComponent = JBangSettingsComponent()
        return settingsComponent!!.getPanel()
    }

    override fun isModified(): Boolean {
        val settings = JBangSettings.getInstance()
        return settingsComponent!!.getJBangExecutablePath() != settings.jbangExecutablePath
    }

    override fun apply() {
        val settings = JBangSettings.getInstance()
        settings.jbangExecutablePath = settingsComponent!!.getJBangExecutablePath()
    }

    override fun reset() {
        val settings = JBangSettings.getInstance()
        settingsComponent!!.setJBangExecutablePath(settings.jbangExecutablePath ?: "")
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
} 