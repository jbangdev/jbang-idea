package dev.jbang.idea.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.runConfigurationType
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import dev.jbang.idea.project.JBangScriptDetector

/**
 * Produces JBang run configurations from right-click context on jbang scripts.
 */
class JBangRunConfigProducer : LazyRunConfigurationProducer<JBangRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        runConfigurationType<JBangConfigurationType>().configurationFactories[0]

    override fun isConfigurationFromContext(
        configuration: JBangRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        return configuration.scriptPath == file.path
    }

    override fun setupConfigurationFromContext(
        configuration: JBangRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (!JBangScriptDetector.isRootScript(file)) return false

        configuration.scriptPath = file.path
        configuration.name = "jbang ${file.name}"
        return true
    }
}
