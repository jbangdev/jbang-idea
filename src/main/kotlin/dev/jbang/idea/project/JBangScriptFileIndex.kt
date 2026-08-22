package dev.jbang.idea.project

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

/**
 * Persisted file-based index that marks JBang root scripts.
 * Survives IDE restarts — no full project scan needed on startup.
 */
class JBangScriptFileIndex : ScalarIndexExtension<String>() {

    override fun getName(): ID<String, Void> = NAME

    override fun getIndexer(): DataIndexer<String, Void, FileContent> = DataIndexer { fileContent ->
        if (JBangScriptDetector.hasJBangMarkers(fileContent.contentAsText)) mapOf(KEY to null)
        else emptyMap()
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getVersion(): Int = 2
    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter {
        JBangScriptDetector.isScriptExtension(it)
    }
    override fun dependsOnFileContent(): Boolean = true

    companion object {
        val NAME: ID<String, Void> = ID.create("jbang.rootScriptIndex")
        private const val KEY = "root"

        /** Returns all JBang root script files in the project. */
        fun findRootScripts(project: Project): Collection<VirtualFile> =
            ReadAction.computeBlocking<Collection<VirtualFile>, Throwable> {
                FileBasedIndex.getInstance().getContainingFiles(NAME, KEY, GlobalSearchScope.projectScope(project))
                    .filter(ProjectFileIndex.getInstance(project)::isInContent)
            }
    }
}
