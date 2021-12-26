package dev.jbang.intellij.plugins.jbang.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import dev.jbang.intellij.plugins.jbang.jbangIcon
import dev.jbang.intellij.plugins.jbang.run.JbangConfigurationType.Companion.ID
import javax.swing.Icon

class JbangConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String {
        return ID
    }

    override fun getOptionsClass(): Class<out BaseState> {
        return JbangRunConfigurationOptions::class.java
    }

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return JbangRunConfiguration(project, this, "JBang")
    }

    override fun getIcon(): Icon {
        return jbangIcon
    }
}