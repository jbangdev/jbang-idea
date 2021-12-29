package dev.jbang.idea.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import dev.jbang.idea.jbangIcon
import javax.swing.Icon

class JBangConfigurationType : ConfigurationType {
    companion object {
        const val ID = "JBangRunConfiguration"
    }

    override fun getDisplayName(): String {
        return "JBang"
    }

    override fun getConfigurationTypeDescription(): String {
        return "JBang run configuration type"
    }

    override fun getIcon(): Icon {
        return jbangIcon
    }

    override fun getId(): String {
        return ID
    }

    override fun getConfigurationFactories(): Array<ConfigurationFactory> {
        return arrayOf(JBangConfigurationFactory(this))
    }


}