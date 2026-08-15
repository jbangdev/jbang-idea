package dev.jbang.idea.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class JBangConfigurable : Configurable {

    private var panel: JPanel? = null
    private val pathField = TextFieldWithBrowseButton()
    private val autoSyncCheckbox = JCheckBox("Auto-sync dependencies on save")

    init {
        @Suppress("DEPRECATION")
        pathField.addBrowseFolderListener(
            "Select JBang Executable", null, null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        )
        // ponytail: addBrowseFolderListener is deprecated but replacement API is not yet stable. Revisit.
    }

    override fun getDisplayName(): String = "JBang"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("JBang path:", pathField)
            .addComponent(autoSyncCheckbox)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = JBangSettings.instance
        return pathField.text != settings.jbangPath || autoSyncCheckbox.isSelected != settings.autoSync
    }

    override fun apply() {
        val settings = JBangSettings.instance
        settings.jbangPath = pathField.text
        settings.autoSync = autoSyncCheckbox.isSelected
    }

    override fun reset() {
        val settings = JBangSettings.instance
        pathField.text = settings.jbangPath
        autoSyncCheckbox.isSelected = settings.autoSync
    }
}
