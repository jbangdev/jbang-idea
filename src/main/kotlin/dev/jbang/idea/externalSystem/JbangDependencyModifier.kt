package dev.jbang.idea.externalSystem

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.externalSystem.ExternalDependencyModificator
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiComment
import dev.jbang.idea.file.JBangScriptFileIndex
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType

class JbangDependencyModifier : ExternalDependencyModificator {

    override fun supports(module: Module): Boolean {
        return JBangScriptFileIndex.findJbangScriptFiles(module).isNotEmpty()
    }

    override fun addDependency(module: Module, descriptor: UnifiedDependency) {

    }

    override fun updateDependency(module: Module, oldDescriptor: UnifiedDependency, newDescriptor: UnifiedDependency) {

    }

    override fun removeDependency(module: Module, descriptor: UnifiedDependency) {

    }

    override fun addRepository(module: Module, repository: UnifiedDependencyRepository) {

    }

    override fun deleteRepository(module: Module, repository: UnifiedDependencyRepository) {

    }

    override fun declaredDependencies(module: Module): MutableList<DeclaredDependency> {
        val scriptFiles = JBangScriptFileIndex.findJbangScriptFiles(module)
        return scriptFiles.map {
            it.toPsiFile(module.project)!!
        }.flatMap { psiFile ->
            psiFile.getChildrenOfType<PsiComment>().filter { it.text.startsWith("//DEPS ") }
                .map { psiComment ->
                    val gav = psiComment.text.substring(7).trim()
                    val parts = gav.split(':')
                    val version = parts.getOrNull(2) ?: ""
                    val dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PSI_ELEMENT, psiComment)
                    DeclaredDependency(parts[0], parts[1], version, null, dataContext)
                }
        }.toMutableList()
    }

    override fun declaredRepositories(module: Module): MutableList<UnifiedDependencyRepository> {
        return mutableListOf()
    }

}