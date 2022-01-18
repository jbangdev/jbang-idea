package dev.jbang.idea.module

import com.intellij.ide.actions.CreateFileFromTemplateAction.createFileFromTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleBuilderListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.SystemInfo
import dev.jbang.idea.jbangIcon
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import java.io.File
import javax.swing.Icon


class JBangModuleBuilder : JavaModuleBuilder(), ModuleBuilderListener {

    init {
        addListener(this)
    }

    override fun getBuilderId(): String {
        return "JBang"
    }

    override fun getNodeIcon(): Icon {
        return jbangIcon
    }

    override fun getPresentableName(): String {
        return "JBang"
    }

    override fun moduleCreated(module: Module) {
        val moduleRootManager = ModuleRootManager.getInstance(module)
        val groovyIncluded = moduleRootManager.orderEntries().allLibrariesAndSdkClassesRoots.any {
            it.name.startsWith("groovy") && it.name.endsWith(".jar")
        }
        val kotlinIncluded = moduleRootManager.orderEntries().allLibrariesAndSdkClassesRoots.any {
            it.name.startsWith("kotlin-stdlib") && it.name.endsWith(".jar")
        }
        var fileTemplateName = "JBang Java"
        if (groovyIncluded) {
            fileTemplateName = "JBang Groovy"
        } else if (kotlinIncluded) {
            fileTemplateName = "JBang Kotlin"
        }
        val roots = moduleRootManager.sourceRoots
        if (roots.isNotEmpty()) {
            val srcRoot = roots[0]
            ApplicationManager.getApplication().runWriteAction {
                val template = FileTemplateManager.getInstance(module.project).getInternalTemplate(fileTemplateName)
                val scriptFile = createFileFromTemplate("Hello", template, srcRoot.toPsiDirectory(module.project)!!, null, false)
                if (scriptFile != null && (SystemInfo.isLinux || SystemInfo.isMac)) {
                    File(scriptFile.virtualFile.path).setExecutable(true)
                }
            }
        }
    }

}