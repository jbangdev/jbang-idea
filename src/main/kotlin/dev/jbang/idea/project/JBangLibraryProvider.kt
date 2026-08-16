package dev.jbang.idea.project

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import dev.jbang.idea.JBangPlugin
import java.io.File

/**
 * Provides jbang's resolved classpath JARs as synthetic libraries.
 * These appear under "External Libraries" and provide code completion/navigation
 * without touching the module's own library table.
 *
 * Coexists with Gradle/Maven — their libraries are separate.
 */
class JBangLibraryProvider : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val service = JBangProjectService.getInstance(project)
        val libs = mutableListOf<SyntheticLibrary>()

        for ((path, info) in service.allRoots) {
            val jars = service.getLibraryRoots(path).filter { it.isValid }
            val sourceRoots = service.getSourceFilesForFile(path).mapNotNull { it.parent }.distinct()
            if (jars.isEmpty() && sourceRoots.isEmpty()) continue

            val scriptName = File(path).name
            val basePath = project.guessProjectDir()?.path
            val displayPath = if (basePath != null && path.startsWith(basePath))
                path.removePrefix(basePath).removePrefix("/")
            else path
            libs.add(JBangSyntheticLibrary(
                name = "jbang: $scriptName",
                rootScriptPath = path,
                displayPath = displayPath,
                project = project,
                classRoots = jars,
                sourceRoots = sourceRoots,
            ))
        }

        return libs
    }
}

private class JBangSyntheticLibrary(
    private val name: String,
    private val rootScriptPath: String,
    private val displayPath: String,
    private val project: Project,
    private val classRoots: List<VirtualFile>,
    private val sourceRoots: List<VirtualFile>,
) : SyntheticLibrary(), ItemPresentation, Navigatable {

    override fun getSourceRoots(): Collection<VirtualFile> = sourceRoots
    override fun getBinaryRoots(): Collection<VirtualFile> = classRoots

    override fun getPresentableText(): String = name
    override fun getLocationString(): String = displayPath
    override fun getIcon(unused: Boolean) = JBangPlugin.icon16

    override fun canNavigate(): Boolean = LocalFileSystem.getInstance().findFileByPath(rootScriptPath) != null
    override fun canNavigateToSource(): Boolean = canNavigate()
    override fun navigate(requestFocus: Boolean) {
        val file = LocalFileSystem.getInstance().findFileByPath(rootScriptPath) ?: return
        FileEditorManager.getInstance(project).openFile(file, requestFocus)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JBangSyntheticLibrary) return false
        return name == other.name && classRoots == other.classRoots
    }

    override fun hashCode(): Int = name.hashCode() * 31 + classRoots.hashCode()
}
