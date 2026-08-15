package dev.jbang.idea.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class CatalogCompletion : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(JsonStringLiteral::class.java),
            Provider,
        )
    }

    private object Provider : CompletionProvider<CompletionParameters>() {
        private val scriptExtensions = setOf("java", "kt", "groovy", "jsh", "jbang")

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            if (parameters.originalFile.name != "jbang-catalog.json") return
            val literal = parameters.position.parent as? JsonStringLiteral ?: return
            if ((literal.parent as? JsonProperty)?.name != "script-ref") return
            val path = literal.text.trim('"')
                .replace(CompletionUtil.DUMMY_IDENTIFIER, "")
                .replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, "")
            if (path.startsWith("http://") || path.startsWith("https://")) return
            val slash = path.lastIndexOf('/')
            val directoryPath = if (slash < 0) "" else path.substring(0, slash + 1)
            val directory = parameters.originalFile.virtualFile?.parent
                ?.let { if (directoryPath.isEmpty()) it else it.findFileByRelativePath(directoryPath) }
                ?: return

            val matches = result.withPrefixMatcher("")
            directory.children.asSequence()
                .filter { !it.name.startsWith('.') && (it.isDirectory || it.extension in scriptExtensions) }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .forEach { matches.addElement(LookupElementBuilder.create(it.name + if (it.isDirectory) "/" else "")) }
        }
    }
}
