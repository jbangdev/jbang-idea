package dev.jbang.idea.project

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
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
    private const val libraryName = "JBang Kotlin (active root)"

    fun update(project: Project, activeRootFile: VirtualFile? = null) {
        val service = JBangProjectService.getInstance(project)
        val path = service.activeRootPath
        val file = activeRootFile ?: path?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val activeModule = file?.let { ModuleUtilCore.findModuleForFile(it, project) }
        val activeUrls = path?.let(service::getLibraryRoots).orEmpty()
            .filter(::isKotlinLibrary).map(VirtualFile::getUrl)

        // Clean up a previously active root in another module as well.
        ModuleManager.getInstance(project).modules.forEach { module ->
            if (ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId() == null) {
                update(module, if (module == activeModule) activeUrls else emptyList())
            }
        }
    }

    private fun update(module: Module, urls: List<String>) {
        val rootModel = ModuleRootManager.getInstance(module).modifiableModel
        val current = rootModel.moduleLibraryTable.getLibraryByName(libraryName)
            ?.getUrls(OrderRootType.CLASSES)?.toList().orEmpty()
        rootModel.dispose()
        if (current == urls) return

        ModuleRootModificationUtil.updateModel(module) { model ->
            model.moduleLibraryTable.getLibraryByName(libraryName)?.let(model.moduleLibraryTable::removeLibrary)
            if (urls.isNotEmpty()) {
                val library = model.moduleLibraryTable.createLibrary(libraryName)
                model.addLibraryEntry(library)
                library.modifiableModel.apply {
                    urls.forEach { addRoot(it, OrderRootType.CLASSES) }
                    commit()
                }
            }
        }
    }

    private fun isKotlinLibrary(root: VirtualFile): Boolean = root.findFileByRelativePath("META-INF")
        ?.children.orEmpty().any { it.name.endsWith(".kotlin_module") }
}
