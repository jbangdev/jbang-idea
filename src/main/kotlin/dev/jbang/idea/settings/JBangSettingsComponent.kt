package dev.jbang.idea.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import dev.jbang.idea.getJBangCmdAbsolutionPath
import javax.swing.JPanel
import javax.swing.JTextField

class JBangSettingsComponent {
    private val mainPanel: JPanel
    private val jbangExecutablePathField = TextFieldWithBrowseButton()
    private val effectivePathField = JTextField().apply {
        isEditable = false
    }

    init {
        jbangExecutablePathField.addBrowseFolderListener(
            "Select JBang Executable",
            "Select the JBang executable file",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )

        // Add a listener to update the effective path when the custom path changes
        jbangExecutablePathField.textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { updateEffectivePath() }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { updateEffectivePath() }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { updateEffectivePath() }
        })

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Custom JBang Path (optional): "), jbangExecutablePathField, 1, false)
            .addLabeledComponent(JBLabel("Effective JBang Path: "), effectivePathField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        updateEffectivePath()
    }

    private fun updateEffectivePath() {
        // Temporarily set the custom path to see what would be used
        val settings = JBangSettings.getInstance()
        val originalPath = settings.jbangExecutablePath
        settings.jbangExecutablePath = jbangExecutablePathField.text
        effectivePathField.text = getJBangCmdAbsolutionPath()
        settings.jbangExecutablePath = originalPath
    }

    fun getPanel(): JPanel {
        return mainPanel
    }

    fun getJBangExecutablePath(): String {
        return jbangExecutablePathField.text
    }

    fun setJBangExecutablePath(path: String) {
        jbangExecutablePathField.text = path
        updateEffectivePath()
    }
} 