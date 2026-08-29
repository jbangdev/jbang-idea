package dev.jbang.idea.project

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/**
 * K2 only creates Kotlin light classes for module-library roots. Keep the
 * per-script synthetic library as the normal path and mirror Kotlin JARs for
 * the currently active standalone script only.
 */
internal object JBangKotlinLibraryMirror {
    private const val libraryNamePrefix = "JBang Kotlin support — "

    fun update(project: Project, activeRootFile: VirtualFile? = null) {
        val service = JBangProjectService.getInstance(project)
        val path = service.activeRootPath
        val file = activeRootFile ?: path?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val activeModule = file?.let { ModuleUtilCore.findModuleForFile(it, project) }
        val activeUrls = path?.let(service::getLibraryRoots).orEmpty()
            .filter(::isKotlinLibrary).map(VirtualFile::getUrl)
        val activeLibraryName = path?.let { libraryNamePrefix + displayPath(project, it) }

        // Clean up a previously active root in another module as well.
        ModuleManager.getInstance(project).modules.forEach { module ->
            if (ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId() == null) {
                val isActive = module == activeModule && activeUrls.isNotEmpty()
                update(module, if (isActive) activeUrls else emptyList(), activeLibraryName.takeIf { isActive })
            }
        }
    }

    private fun update(module: Module, urls: List<String>, libraryName: String?) {
        val rootModel = ModuleRootManager.getInstance(module).modifiableModel
        val managedLibraries = rootModel.moduleLibraryTable.libraries.filter {
            it.name?.startsWith(libraryNamePrefix) == true
        }
        val unchanged = managedLibraries.size == 1 && managedLibraries.single().let {
            it.name == libraryName && it.getUrls(OrderRootType.CLASSES).toList() == urls
        }
        rootModel.dispose()
        if (unchanged) return

        ModuleRootModificationUtil.updateModel(module) { model ->
            model.moduleLibraryTable.libraries
                .filter { it.name?.startsWith(libraryNamePrefix) == true }
                .forEach(model.moduleLibraryTable::removeLibrary)
            if (urls.isNotEmpty() && libraryName != null) {
                val library = model.moduleLibraryTable.createLibrary(libraryName)
                model.addLibraryEntry(library)
                library.modifiableModel.apply {
                    urls.forEach { addRoot(it, OrderRootType.CLASSES) }
                    commit()
                }
            }
        }
    }

    private fun displayPath(project: Project, path: String): String {
        val basePath = project.guessProjectDir()?.path ?: return path
        return path.removePrefix("$basePath/").takeIf { it != path } ?: path
    }

    private fun isKotlinLibrary(root: VirtualFile): Boolean = root.findFileByRelativePath("META-INF")
        ?.children.orEmpty().any { it.name.endsWith(".kotlin_module") }
}
