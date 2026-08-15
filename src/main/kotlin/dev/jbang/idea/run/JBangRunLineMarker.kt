package dev.jbang.idea.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import dev.jbang.idea.JBangPlugin
import dev.jbang.idea.project.JBangScriptDetector

/**
 * Shows a run gutter icon for jbang scripts on:
 * 1. The first directive comment (//DEPS, //JAVA, shebang)
 * 2. The class name identifier (for scripts without main)
 * 3. The main() method name identifier
 *
 * Only fires if the file is a jbang root script.
 */
class JBangRunLineMarker : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        val file = element.containingFile ?: return null

        // Case 1: directive comment — only on the first one. Doc comments are
        // non-leaf PSI, so attach their marker to the first token instead.
        val comment = when {
            element is PsiDocComment -> return null
            element.parent is PsiDocComment && element === element.parent.firstChild -> element.parent
            element is PsiComment -> element
            else -> null
        }
        if (comment != null) {
            val text = comment.text
            val isShebang = text.startsWith(JBangPlugin.SHEBANG)
            val isDirective = JBangScriptDetector.isRootDirectiveLine(text)
            if ((isShebang || isDirective) && isFirstJBangMarker(comment)) {
                return jbangInfo(file.name)
            }
            return null
        }

        // Cases 2 & 3: class/main identifiers in jbang scripts
        if (element !is PsiIdentifier) return null
        if (!isJBangFile(file)) return null

        val parent = element.parent

        // Case 2: class name identifier — first/only class
        if (parent is PsiClass && element == parent.nameIdentifier) {
            if (isFirstClass(parent)) {
                return jbangInfo(file.name)
            }
        }

        // Case 3: main() method identifier
        if (parent is PsiMethod && element == parent.nameIdentifier && parent.name == "main") {
            return jbangInfo(file.name)
        }

        return null
    }

    private fun jbangInfo(fileName: String): Info {
        val actions = ExecutorAction.getActions(0)
        return Info(JBangPlugin.icon16, actions) { "Run $fileName with JBang" }
    }

    private fun isFirstClass(clazz: PsiClass): Boolean {
        val file = clazz.containingFile ?: return false
        val firstClass = com.intellij.psi.util.PsiTreeUtil.findChildOfType(file, PsiClass::class.java)
        return firstClass === clazz
    }

    /**
     * Check if the file contains jbang markers by scanning PSI.
     * Used for class/main markers where we don't have a VirtualFile on disk.
     */
    private fun isJBangFile(file: PsiFile): Boolean {
        // Try VirtualFile first (works for real files on disk)
        val vFile = file.virtualFile
        if (vFile != null && JBangScriptDetector.isRootScript(vFile)) return true

        // Fallback: scan PSI text (works for in-memory/test files and files outside source root)
        return hasJBangMarkerInPsi(file)
    }

    private fun hasJBangMarkerInPsi(file: PsiFile): Boolean {
        var found = false
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (found) return
                if (element is PsiComment || element is PsiDocComment) {
                    val t = element.text
                    if (t.startsWith(JBangPlugin.SHEBANG) || JBangScriptDetector.isRootDirectiveLine(t)) {
                        found = true
                        return
                    }
                }
                super.visitElement(element)
            }
        })
        return found
    }

    private fun isFirstJBangMarker(element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        var found: PsiElement? = null
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(el: PsiElement) {
                if (found != null) return
                if (el is PsiComment || el is PsiDocComment) {
                    val t = el.text
                    if (t.startsWith(JBangPlugin.SHEBANG) || JBangScriptDetector.isRootDirectiveLine(t)) {
                        found = el
                        return
                    }
                }
                super.visitElement(el)
            }
        })
        return found === element
    }
}
