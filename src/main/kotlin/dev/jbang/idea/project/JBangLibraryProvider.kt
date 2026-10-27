package dev.jbang.idea.project

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.JavaSyntheticLibrary
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import dev.jbang.idea.JBangFeatureTips
import dev.jbang.idea.JBangPlugin
import java.io.File

/**
 * Provides jbang's resolved classpath JARs as synthetic libraries.
 * These appear under "External Libraries" and provide code completion/navigation
 * without touching the module's own library table.
 *
 * Coexists with Gradle/Maven — their libraries are separate.
 */
class JBangLibraryProvider(
    private val showFeatureTip: (Project) -> Unit = JBangFeatureTips::showClasspath,
) : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val service = JBangProjectService.getInstance(project)
        val libs = mutableListOf<SyntheticLibrary>()

        for ((path, info) in service.allRoots) {
            val jars = service.getLibraryRoots(path).filter { it.isValid }
            val sourceRoots = service.getSourceFilesForFile(path).mapNotNull { it.parent }.distinct()
            if (jars.isEmpty() && sourceRoots.isEmpty()) continue

            val basePath = project.guessProjectDir()?.path
            val displayPath = if (basePath != null && path.startsWith(basePath))
                path.removePrefix(basePath).removePrefix("/")
            else path
            libs.add(JBangSyntheticLibrary(
                name = "jbang: $displayPath",
                rootScriptPath = path,
                displayPath = displayPath,
                project = project,
                classRoots = jars,
                sourceRoots = sourceRoots,
            ))
        }

        if (libs.isNotEmpty()) showFeatureTip(project)
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
) : JavaSyntheticLibrary(
    // comparisonId: identifies this library for platform equality/indexing.
    rootScriptPath,
    sourceRoots,
    classRoots,
    emptySet(),
), ItemPresentation, Navigatable {
    // Extends JavaSyntheticLibrary (not plain SyntheticLibrary) so its JAR roots are
    // registered in PackageIndex/fileSetsByPackagePrefix. A plain SyntheticLibrary gets
    // DummyWorkspaceFileSetData, leaving findPackage() null so Java wildcard/on-demand
    // imports never resolve while explicit imports/FQNs do (IDEA-393077, issue #163).

    override fun getPresentableText(): String = name
    override fun getLocationString(): String? = null // path is already in the name
    override fun getIcon(unused: Boolean) = JBangPlugin.icon16

    override fun canNavigate(): Boolean = LocalFileSystem.getInstance().findFileByPath(rootScriptPath) != null
    override fun canNavigateToSource(): Boolean = canNavigate()
    override fun navigate(requestFocus: Boolean) {
        val file = LocalFileSystem.getInstance().findFileByPath(rootScriptPath) ?: return
        FileEditorManager.getInstance(project).openFile(file, requestFocus)
    }

    // No equals()/hashCode() override: ImmutableSyntheticLibrary (JavaSyntheticLibrary's
    // parent) already implements them from the comparisonId (rootScriptPath) plus roots,
    // and marks them @ApiStatus.Internal so overriding them is forbidden (verifyPlugin
    // INTERNAL_API_USAGES). The base identity matches what we need.
}
