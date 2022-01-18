package dev.jbang.idea.file

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import dev.jbang.idea.isJBangScript
import dev.jbang.idea.isJBangScriptFile


class JBangScriptFileIndex : ScalarIndexExtension<String>() {
    override fun getName(): ID<String, Void> = NAME

    override fun getIndexer(): DataIndexer<String, Void, FileContent> {
        return DataIndexer {
            if (isJBangScript(it.contentAsText)) {
                mapOf("jbang" to null)
            } else {
                emptyMap()
            }
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> {
        return EnumeratorStringDescriptor.INSTANCE
    }

    override fun getVersion(): Int {
        return 1
    }

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter {
            isJBangScriptFile(it.name)
        }
    }

    override fun dependsOnFileContent(): Boolean {
        return true
    }

    companion object {
        val NAME = ID.create<String, Void?>("jbang.scriptFileIndex")

        fun findJbangScriptFiles(module: Module): MutableCollection<VirtualFile> {
            val fileBasedIndex = FileBasedIndex.getInstance()
            return ReadAction.compute<MutableCollection<VirtualFile>, Throwable> {
                fileBasedIndex.getContainingFiles(NAME, "jbang", GlobalSearchScope.moduleScope(module))
            }
        }
    }
}