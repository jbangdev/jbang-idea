package dev.jbang.idea.navigation

import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

class JBangNavigation : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiComment(), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val match = DIRECTIVE.matchEntire(element.text) ?: return PsiReference.EMPTY_ARRAY
                val arguments = match.groups[2] ?: return PsiReference.EMPTY_ARRAY
                return TOKEN.findAll(arguments.value).map { token ->
                    val mappedPath = if (match.groupValues[1] == "FILES") token.value.substringAfter('=') else token.value
                    val start = arguments.range.first + token.range.first + token.value.length - mappedPath.length
                    LocalPathReference(element, TextRange(start, start + mappedPath.length), mappedPath)
                }.toList().toTypedArray()
            }
        })
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(JsonStringLiteral::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val literal = element as JsonStringLiteral
                val property = literal.parent as? JsonProperty ?: return PsiReference.EMPTY_ARRAY
                val path = literal.value
                if (property.name != "script-ref" || path.isRemoteOrAbsolute()) return PsiReference.EMPTY_ARRAY
                return arrayOf(LocalPathReference(literal, TextRange(1, literal.textLength - 1), path))
            }
        })
    }

    private class LocalPathReference(
        element: PsiElement,
        range: TextRange,
        private val path: String,
    ) : PsiReferenceBase<PsiElement>(element, range, true) {
        override fun resolve(): PsiElement? {
            val base = element.containingFile.virtualFile.parent ?: return null
            val target = base.findFileByRelativePath(path) ?: return null
            return PsiManager.getInstance(element.project).findFile(target)
        }

        override fun getVariants(): Array<Any> = emptyArray()
    }

    companion object {
        private val DIRECTIVE = Regex("//(SOURCES|FILES)\\s+(.+)")
        private val TOKEN = Regex("\\S+")
        private fun String.isRemoteOrAbsolute() =
            startsWith("http://") || startsWith("https://") || startsWith("/")
    }
}
