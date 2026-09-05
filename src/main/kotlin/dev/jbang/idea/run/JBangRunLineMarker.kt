package dev.jbang.idea.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.*
import dev.jbang.idea.JBangFeatureTips
import dev.jbang.idea.JBangPlugin
import dev.jbang.idea.project.JBangScriptDetector
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

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
        // PsiDocCommentBase is the cross-language base for doc comments (Java's
        // PsiDocComment and Kotlin's KDoc both implement it), so a leading `///`
        // shebang parsed as a doc comment is handled the same way in every language.
        val comment = commentForMarker(element)
        if (comment != null) {
            val text = comment.text
            val isShebang = text.startsWith(JBangPlugin.SHEBANG)
            val isDirective = JBangScriptDetector.isRootDirectiveLine(text)
            if ((isShebang || isDirective) && isFirstJBangMarker(comment)) {
                return jbangInfo(file.name, element)
            }
            return null
        }

        // Cases 2 & 3: class/main identifiers in jbang scripts.
        // Accept both Java PSI (PsiIdentifier) and Kotlin PSI (KtClass/KtNamedFunction
        // name identifiers are LeafPsiElement) so the marker works for .kt files too.
        val parent = element.parent
        val isNameId = element is PsiIdentifier ||
            (parent is KtClass && element == parent.nameIdentifier) ||
            (parent is KtNamedFunction && element == parent.nameIdentifier)
        if (!isNameId) return null
        if (!isJBangFile(file)) return null

        // Case 2: class name identifier — first/only class
        if (parent is PsiClass && element == parent.nameIdentifier) {
            if (isFirstClass(parent)) return jbangInfo(file.name, element)
        }
        if (parent is KtClass && element == parent.nameIdentifier) {
            if (isFirstKtClass(parent)) return jbangInfo(file.name, element)
        }

        // Case 3: main() method identifier
        if (parent is PsiMethod && element == parent.nameIdentifier && parent.name == "main") {
            return jbangInfo(file.name, element)
        }
        if (parent is KtNamedFunction && element == parent.nameIdentifier && parent.name == "main") {
            return jbangInfo(file.name, element)
        }

        return null
    }

    internal fun commentForMarker(element: PsiElement): PsiElement? = when {
        element is PsiDocCommentBase -> null
        element.parent is PsiDocCommentBase && element === element.parent.firstChild -> element.parent
        element is PsiComment -> element
        else -> null
    }

    private fun jbangInfo(fileName: String, element: PsiElement): Info {
        val line = element.containingFile.viewProvider.document?.getLineNumber(element.textOffset) ?: 0
        JBangFeatureTips.showRun(element.project, line)
        val actions = ExecutorAction.getActions(0)
        return Info(JBangPlugin.icon16, actions) { "Run $fileName with JBang" }
    }

    private fun isFirstClass(clazz: PsiClass): Boolean {
        val file = clazz.containingFile ?: return false
        val firstClass = com.intellij.psi.util.PsiTreeUtil.findChildOfType(file, PsiClass::class.java)
        return firstClass === clazz
    }

    private fun isFirstKtClass(clazz: KtClass): Boolean {
        val file = clazz.containingFile ?: return false
        val firstClass = com.intellij.psi.util.PsiTreeUtil.findChildOfType(file, KtClass::class.java)
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
                if (element is PsiComment || element is PsiDocCommentBase) {
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
                if (el is PsiComment || el is PsiDocCommentBase) {
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
