package dev.jbang.idea.ui;

import dev.jbang.idea.ScriptInfo;

import javax.swing.*;

public class JBangToolWindow {
    private JPanel toolWindowContent;
    private JTextField originalResourceField;
    private JTextField javaField;
    private JTextArea dependenciesTextArea;

    public JPanel getContent() {
        return toolWindowContent;
    }

    public void update(ScriptInfo scriptInfo) {
        if (scriptInfo.getOriginalResource() != null) {
            final String[] parts = scriptInfo.getOriginalResource().split("[/\\\\]");
            originalResourceField.setText(parts[parts.length - 1]);
        }
        javaField.setText(scriptInfo.getJavaVersion() != null ? scriptInfo.getJavaVersion() : scriptInfo.getRequestedJavaVersion());
        if (scriptInfo.getDependencies() != null) {
            dependenciesTextArea.setText(String.join("\n", scriptInfo.getDependencies()));
        }
    }
}

