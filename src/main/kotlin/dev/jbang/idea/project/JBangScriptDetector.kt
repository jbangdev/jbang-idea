package dev.jbang.idea.project

import com.intellij.openapi.vfs.VirtualFile
import dev.jbang.idea.JBangPlugin

/**
 * Detects whether a file is a jbang root script (has its own classpath)
 * or a plain source file included via //SOURCES.
 */
object JBangScriptDetector {

    /** File extensions that can be jbang scripts. */
    private val SCRIPT_EXTENSIONS = setOf("java", "kt", "groovy", "jsh", "jbang")

    /** Returns true if this file could be a jbang script based on extension. */
    fun isScriptExtension(file: VirtualFile): Boolean {
        return file.extension?.lowercase() in SCRIPT_EXTENSIONS
    }

    /**
     * Returns true if this file is a jbang **root** script.
     * A root has build directives (//DEPS, //JAVA, etc.), the shebang, or is a build file.
     */
    fun isRootScript(file: VirtualFile): Boolean {
        if (!isScriptExtension(file)) return false
        if (file.name in JBangPlugin.BUILD_FILE_NAMES) return true
        return hasJBangMarkers(file)
    }

    /**
     * Scans the first N lines of text for jbang markers.
     * Used by both VirtualFile detection and the file-based index.
     */
    fun hasJBangMarkers(content: CharSequence): Boolean {
        var lineCount = 0
        for (line in content.lineSequence()) {
            if (lineCount++ > 200) break
            val trimmed = line.trim()
            if (trimmed.startsWith(JBangPlugin.SHEBANG)) return true
            if (isDirectiveLine(trimmed)) return true
        }
        return false
    }

    private fun hasJBangMarkers(file: VirtualFile): Boolean {
        return try {
            file.inputStream.bufferedReader().use { hasJBangMarkers(it.readText()) }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns true if the line is a jbang directive comment like `//DEPS ...` or `//JAVA ...`.
     */
    fun isDirectiveLine(line: String): Boolean {
        if (!line.startsWith("//")) return false
        if (line.startsWith(JBangPlugin.SHEBANG)) return true
        val afterSlashes = line.substring(2)
        val directive = afterSlashes.takeWhile { it.isLetter() || it == '_' }
        return directive in JBangPlugin.ROOT_DIRECTIVES || directive == "SOURCES" || directive == "FILES"
    }

    /**
     * Returns true if the line is specifically a root-marking directive (not SOURCES/FILES).
     */
    fun isRootDirectiveLine(line: String): Boolean {
        if (!line.startsWith("//")) return false
        val afterSlashes = line.substring(2)
        val directive = afterSlashes.takeWhile { it.isLetter() || it == '_' }
        return directive in JBangPlugin.ROOT_DIRECTIVES
    }
}
