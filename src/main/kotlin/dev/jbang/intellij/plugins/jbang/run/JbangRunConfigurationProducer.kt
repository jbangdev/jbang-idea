package dev.jbang.intellij.plugins.jbang.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.runConfigurationType
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

class JbangRunConfigurationProducer : LazyRunConfigurationProducer<JbangRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return runConfigurationType<JbangConfigurationType>().configurationFactories[0]
    }

    override fun isConfigurationFromContext(configuration: JbangRunConfiguration, context: ConfigurationContext): Boolean {
        val location = context.location ?: return false
        val file = location.virtualFile ?: return false
        if (!isAcceptableFileType(file) || !file.isInLocalFileSystem) return false
        val scriptName = configuration.getScriptName()
        return scriptName != null && file.path.endsWith(scriptName)
    }

    override fun setupConfigurationFromContext(configuration: JbangRunConfiguration, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val psiFile = sourceElement.get()?.containingFile ?: return false
        val virtualFile = psiFile.virtualFile ?: return false
        if (!isAcceptableFileType(virtualFile) || !virtualFile.isInLocalFileSystem) return false
        val code = psiFile.text
        // jbang code check: '//DEPS' and '///usr/bin/env jbang'
        if (!(code.contains("///usr/bin/env jbang") || code.contains("//DEPS "))) return false
        val project = psiFile.project
        configuration.setScriptName(virtualFile.path.substring(project.basePath!!.length + 1))
        configuration.name = virtualFile.name
        return true
    }

    private fun isAcceptableFileType(virtualFile: VirtualFile): Boolean {
        val name = virtualFile.name
        return name.endsWith(".java")
                || name.endsWith(".kt")
                || name.endsWith(".jsh")
                || name.endsWith(".groovy")
    }

}