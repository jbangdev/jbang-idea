package dev.jbang.intellij.plugins.jbang.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Factory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField


class JbangRunSettingsEditor(factory: Factory<JbangRunConfiguration>) : SettingsEditor<JbangRunConfiguration>(factory) {
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

    override fun resetEditorFrom(demoRunConfiguration: JbangRunConfiguration) {
        myScriptName.component.text = demoRunConfiguration.getScriptName() ?: ""
        myScriptOptions.component.text = demoRunConfiguration.getScriptOptions() ?: ""
        myScriptArgs.component.text = demoRunConfiguration.getScriptArgs() ?: ""
    }

    override fun applyEditorTo(demoRunConfiguration: JbangRunConfiguration) {
        demoRunConfiguration.setScriptName(myScriptName.component.text)
        demoRunConfiguration.setScriptOptions(myScriptOptions.component.text)
        demoRunConfiguration.setScriptArgs(myScriptArgs.component.text)
    }

    override fun createEditor(): JComponent {
        return myPanel
    }



}