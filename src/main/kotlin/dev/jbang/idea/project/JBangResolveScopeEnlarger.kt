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
            service.getSourceFilesForFile(file.path).mapNotNull { it.parent }.distinct()
                .takeIf { it.isNotEmpty() }?.let {
                    add(GlobalSearchScopesCore.directoriesScope(project, true, *it.toTypedArray()))
                }
        }
        return scopes.takeIf { it.isNotEmpty() }?.let { GlobalSearchScope.union(it) }
    }
}
