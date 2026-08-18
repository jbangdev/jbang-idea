package dev.jbang.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import dev.jbang.idea.cli.TemplateInfo
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Single dialog for creating a JBang script: template (optional) + file name.
 * If no template is selected, `jbang init` uses the file extension to pick a default.
 * OK is disabled until a non-blank name is entered.
 */
class JBangCreateScriptDialog(
    project: Project,
    templates: List<TemplateInfo>,
) : DialogWrapper(project) {

    internal val templateList = JBList(templates).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer { _, value, _, isSelected, _ ->
            javax.swing.JLabel(if (value.description.isNotBlank()) "${value.name} — ${value.description}" else value.name).apply {
                isOpaque = true
                border = javax.swing.BorderFactory.createEmptyBorder(2, 6, 2, 6)
                if (isSelected) {
                    background = javax.swing.UIManager.getColor("List.selectionBackground")
                    foreground = javax.swing.UIManager.getColor("List.selectionForeground")
                }
            }
        }
    }

    internal val nameField = JBTextField(30)

    val selectedTemplate: String? get() = templateList.selectedValue?.name
    val scriptName: String get() = nameField.text.trim()

    init {
        title = "New JBang Script"
        templateList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val suggested = JBangCreateScriptAction.suggestFileName(templateList.selectedValue?.name)
                if (nameField.text.isBlank() || nameField.text == lastSuggested) {
                    nameField.text = suggested
                }
                lastSuggested = suggested
            }
        }
        nameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateOk()
            override fun removeUpdate(e: DocumentEvent) = updateOk()
            override fun changedUpdate(e: DocumentEvent) = updateOk()
        })
        init()
        updateOk()
    }

    private var lastSuggested = ""

    private fun updateOk() {
        isOKActionEnabled = nameField.text.isNotBlank()
    }

    override fun createCenterPanel(): JComponent {
        val scrollPane = JBScrollPane(templateList).apply {
            preferredSize = java.awt.Dimension(400, 200)
        }
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Template (optional):", scrollPane)
            .addLabeledComponent("File name:", nameField)
            .panel
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isBlank()) return ValidationInfo("File name is required", nameField)
        return null
    }

    override fun getPreferredFocusedComponent() = nameField
}
