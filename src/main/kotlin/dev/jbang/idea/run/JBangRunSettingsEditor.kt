package dev.jbang.idea.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField


class JBangRunSettingsEditor : SettingsEditor<JBangRunConfiguration>() {
    private val myPanel: JPanel = JPanel()
    private var myScriptName: LabeledComponent<TextFieldWithBrowseButton> = LabeledComponent()
    private var myScriptOptions: LabeledComponent<JBTextField> = LabeledComponent()
    private var myScriptArgs: LabeledComponent<JBTextField> = LabeledComponent()
    private var myEnvVariables: LabeledComponent<JBTextArea> = LabeledComponent()

    init {
        myPanel.layout = BoxLayout(myPanel, BoxLayout.Y_AXIS)
        myScriptName.component = TextFieldWithBrowseButton()
        myScriptOptions.component = JBTextField()
        myScriptArgs.component = JBTextField()
        myEnvVariables.component = JBTextArea(3,0)
        myScriptName.label.text = "Script file or Catalog alias"
        myScriptOptions.label.text = "Script options"
        myScriptArgs.label.text = "Script args"
        myEnvVariables.label.text = "Env variables(.env style):"
        myPanel.add(myScriptName)
        myPanel.add(myScriptOptions)
        myPanel.add(myScriptArgs)
        myPanel.add(myEnvVariables)
    }

    override fun resetEditorFrom(jBangRunConfiguration: JBangRunConfiguration) {
        myScriptName.component.text = jBangRunConfiguration.getScriptName() ?: ""
        myScriptOptions.component.text = jBangRunConfiguration.getScriptOptions() ?: ""
        myScriptArgs.component.text = jBangRunConfiguration.getScriptArgs() ?: ""
        myEnvVariables.component.text = jBangRunConfiguration.getEnvVariables() ?: ""
    }

    override fun applyEditorTo(jBangRunConfiguration: JBangRunConfiguration) {
        jBangRunConfiguration.setScriptName(myScriptName.component.text)
        jBangRunConfiguration.setScriptOptions(myScriptOptions.component.text)
        jBangRunConfiguration.setScriptArgs(myScriptArgs.component.text)
        jBangRunConfiguration.setEnvVariables(myEnvVariables.component.text)
    }

    override fun createEditor(): JComponent {
        return myPanel
    }


}