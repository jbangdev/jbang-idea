package dev.jbang.idea.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField


class JBangRunSettingsEditor : SettingsEditor<JBangRunConfiguration>() {
    private val myPanel: JPanel = JPanel()
    private var myScriptName: LabeledComponent<TextFieldWithBrowseButton> = LabeledComponent()
    private var myScriptOptions: LabeledComponent<JTextField> = LabeledComponent()
    private var myScriptArgs: LabeledComponent<JTextField> = LabeledComponent()

    init {
        myPanel.layout = BoxLayout(myPanel, BoxLayout.Y_AXIS)
        myScriptName.component = TextFieldWithBrowseButton()
        myScriptOptions.component = JTextField()
        myScriptArgs.component = JTextField()
        myScriptName.label.text = "Script file"
        myScriptOptions.label.text = "Script options"
        myScriptArgs.label.text = "Script args"
        myPanel.add(myScriptName)
        myPanel.add(myScriptOptions)
        myPanel.add(myScriptArgs)
    }

    override fun resetEditorFrom(jBangRunConfiguration: JBangRunConfiguration) {
        myScriptName.component.text = jBangRunConfiguration.getScriptName() ?: ""
        myScriptOptions.component.text = jBangRunConfiguration.getScriptOptions() ?: ""
        myScriptArgs.component.text = jBangRunConfiguration.getScriptArgs() ?: ""
    }

    override fun applyEditorTo(jBangRunConfiguration: JBangRunConfiguration) {
        jBangRunConfiguration.setScriptName(myScriptName.component.text)
        jBangRunConfiguration.setScriptOptions(myScriptOptions.component.text)
        jBangRunConfiguration.setScriptArgs(myScriptArgs.component.text)
    }

    override fun createEditor(): JComponent {
        return myPanel
    }


}