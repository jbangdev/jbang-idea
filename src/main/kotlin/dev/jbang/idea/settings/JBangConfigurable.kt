package dev.jbang.idea.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import dev.jbang.idea.cli.JBangCli
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.awt.FlowLayout
import javax.swing.*

class JBangConfigurable : Configurable {

    private var panel: JPanel? = null
    private val pathField = TextFieldWithBrowseButton()
    private val resolvedLabel = JLabel()
    private val autoSyncCheckbox = JCheckBox("Auto-sync dependencies on save")
    private val askToOpenRootCheckbox = JCheckBox("Ask to open a root selected from the status bar")

    init {
        @Suppress("DEPRECATION")
        pathField.addBrowseFolderListener(
            "Select JBang Executable", null, null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        )
        // ponytail: addBrowseFolderListener is deprecated but replacement API is not yet stable. Revisit.
        pathField.textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = updateResolved()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = updateResolved()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) = updateResolved()
        })
    }

    override fun getDisplayName(): String = "JBang"

    override fun createComponent(): JComponent {
        val installButton = JButton("Install JBang\u2026").apply {
            addActionListener { installJBang() }
        }
        val downloadLink = JButton("Download page\u2026").apply {
            addActionListener { BrowserUtil.browse("https://www.jbang.dev/download") }
            isBorderPainted = false
            isContentAreaFilled = false
            foreground = UIUtil.getLabelInfoForeground()
        }
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(installButton)
            add(Box.createHorizontalStrut(8))
            add(downloadLink)
        }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("JBang path:", pathField)
            .addComponent(resolvedLabel)
            .addComponent(buttonPanel)
            .addSeparator()
            .addComponent(autoSyncCheckbox)
            .addComponent(askToOpenRootCheckbox)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        updateResolved()
        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = JBangSettings.instance
        return pathField.text != settings.jbangPath ||
            autoSyncCheckbox.isSelected != settings.autoSync ||
            askToOpenRootCheckbox.isSelected != settings.askToOpenSelectedRoot
    }

    override fun apply() {
        val settings = JBangSettings.instance
        settings.jbangPath = pathField.text
        settings.autoSync = autoSyncCheckbox.isSelected
        settings.askToOpenSelectedRoot = askToOpenRootCheckbox.isSelected
        if (settings.askToOpenSelectedRoot) settings.openSelectedRootWithoutAsking = false
        updateResolved()
    }

    override fun reset() {
        val settings = JBangSettings.instance
        pathField.text = settings.jbangPath
        autoSyncCheckbox.isSelected = settings.autoSync
        askToOpenRootCheckbox.isSelected = settings.askToOpenSelectedRoot
        updateResolved()
    }

    private fun updateResolved() {
        val customPath = pathField.text.trim()
        val resolved = if (customPath.isNotBlank()) {
            // User specified a path — check that specific file
            if (java.io.File(customPath).canExecute()) customPath else null
        } else {
            JBangCli.resolveJBangPath()
        }
        if (resolved != null) {
            resolvedLabel.text = "Resolved: $resolved"
            resolvedLabel.foreground = UIUtil.getLabelForeground()
        } else if (customPath.isNotBlank()) {
            resolvedLabel.text = "Not found: $customPath"
            resolvedLabel.foreground = UIUtil.getErrorForeground()
        } else {
            resolvedLabel.text = "Not found — install JBang or set the path above"
            resolvedLabel.foreground = UIUtil.getErrorForeground()
        }
    }

    private fun installJBang() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        val cmd = JBangCli.installCommand()
        val ok = MessageDialogBuilder.okCancel(
            "Install JBang",
            "This will open a terminal and run:\n\n$cmd\n\nProceed?",
        ).ask(project)
        if (!ok) return
        try {
            val twm = ToolWindowManager.getInstance(project)
            val toolWindow = twm.getToolWindow("Terminal") ?: return
            toolWindow.activate {
                val manager = TerminalToolWindowManager.getInstance(project)
                val state = TerminalTabState().apply {
                    myTabName = "Install JBang"
                    myIsUserDefinedTabTitle = true
                }
                val widget = manager.createNewSession(manager.terminalRunner, state, toolWindow.contentManager)
                val shell = ShellTerminalWidget.asShellJediTermWidget(widget)
                if (shell != null) {
                    com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                        for (i in 1..40) {
                            Thread.sleep(250)
                            try { if (shell.processTtyConnector != null) break } catch (_: Exception) {}
                        }
                        Thread.sleep(500)
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            try {
                                shell.executeWithTtyConnector { tty -> tty.write((cmd + "\n").toByteArray()) }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {
            BrowserUtil.browse("https://www.jbang.dev/download")
        }
    }
}
