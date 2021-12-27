package dev.jbang.intellij.plugins.jbang.actions

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.LabeledComponent
import dev.jbang.catalog.Catalog
import dev.jbang.catalog.Template
import javax.swing.*


class JbangTemplatesDialogWrapper : DialogWrapper(true) {
    private var myScriptName: LabeledComponent<JTextField> = LabeledComponent()
    private var templateList: LabeledComponent<JComboBox<String>> = LabeledComponent()

    init {
        title = "Init from JBang Templates"
        myScriptName.label.text = "Script name"
        myScriptName.component = JTextField(32)
        templateList.label.text = "Templates"
        templateList.component = createTemplateComboBox()
        init()
        myScriptName.component.requestFocus()
    }

    override fun createCenterPanel(): JComponent {
        val dialogPanel = JPanel()
        dialogPanel.layout = BoxLayout(dialogPanel, BoxLayout.Y_AXIS)
        dialogPanel.add(myScriptName)
        dialogPanel.add(templateList)
        return dialogPanel
    }

    private fun createTemplateComboBox(): JComboBox<String> {
        val templates = Catalog.getBuiltin().templates.map { (name: String, template: Template) -> "${name}: ${template.description}" }.toTypedArray()
        return ComboBox(templates)
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return myScriptName.component
    }

    fun getScriptFileName(): String {
        return myScriptName.component.text
    }

    fun getTemplateName(): String {
        val selectedItem = templateList.component.selectedItem!! as String
        return selectedItem.substring(0, selectedItem.indexOf(':'))
    }
}