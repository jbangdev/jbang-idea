package dev.jbang.idea.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.runConfigurationType
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import dev.jbang.idea.getJBangDirective
import dev.jbang.idea.isJBangScript
import dev.jbang.idea.isJBangScriptFile
import org.jetbrains.kotlin.psi.psiUtil.getTextWithLocation

class JBangRunConfigurationProducer : LazyRunConfigurationProducer<JBangRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return runConfigurationType<JBangConfigurationType>().configurationFactories[0]
    }

    override fun isConfigurationFromContext(configuration: JBangRunConfiguration, context: ConfigurationContext): Boolean {
        val location = context.location ?: return false
        val file = location.virtualFile ?: return false
        if (!isAcceptableFileType(file) || !file.isInLocalFileSystem) return false
        val scriptName = configuration.getScriptName()
        return scriptName != null && file.path.endsWith(scriptName)
    }

    override fun setupConfigurationFromContext(configuration: JBangRunConfiguration, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val psiFile = sourceElement.get()?.containingFile ?: return false
        val virtualFile = psiFile.virtualFile ?: return false
        if (!isAcceptableFileType(virtualFile) || !virtualFile.isInLocalFileSystem) return false
        // jbang code check
        if (!isJBangScript(psiFile.text)) return false
        val psiLocation = context.psiLocation!!
        val textWithLocation = psiLocation.getTextWithLocation()
        if (getJBangDirective(textWithLocation.trim('\'')) != null) {
            val project = psiFile.project
            val scriptPath = virtualFile.path
            val projectBasePath = project.basePath!!
            if (scriptPath.startsWith(projectBasePath)) {
                configuration.setScriptName(scriptPath.substring(projectBasePath.length + 1))
            } else {
                configuration.setScriptName(scriptPath)
            }
            configuration.name = virtualFile.name + " by JBang"
            return true
        }
        return false
    }

    private fun isAcceptableFileType(virtualFile: VirtualFile): Boolean {
        return isJBangScriptFile(virtualFile.name)
    }

}