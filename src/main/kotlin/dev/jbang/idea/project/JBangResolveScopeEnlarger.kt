package dev.jbang.idea.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ResolveScopeEnlarger
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.SearchScope

/** Adds a root script's synthetic JBang libraries to Java's per-file resolve scope. */
class JBangResolveScopeEnlarger : ResolveScopeEnlarger() {

    override fun getAdditionalResolveScope(file: VirtualFile, project: Project): SearchScope? {
        val service = JBangProjectService.getInstance(project)
        val scopes = buildList {
            service.getLibraryRootsForFile(file.path).takeIf { it.isNotEmpty() }?.let {
                add(GlobalSearchScopesCore.directoriesScope(project, true, *it.toTypedArray()))
            }
            service.getSourceFilesForFile(file.path).takeIf { it.isNotEmpty() }?.let { files ->
                val included = files.toSet()
                add(object : GlobalSearchScope(project) {
                    override fun contains(file: VirtualFile) = file in included
                    override fun compare(file1: VirtualFile, file2: VirtualFile) = 0
                    override fun isSearchInModuleContent(module: com.intellij.openapi.module.Module) = true
                    override fun isSearchInLibraries() = true
                })
            }
        }
        return scopes.takeIf { it.isNotEmpty() }?.let { GlobalSearchScope.union(it) }
    }
}
