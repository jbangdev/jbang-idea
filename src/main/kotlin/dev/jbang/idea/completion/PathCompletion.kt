package dev.jbang.idea.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.util.ProcessingContext

/** Relative path completion for //SOURCES and //FILES. */
class PathCompletion : CompletionContributor(), DumbAware {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(PsiComment::class.java),
            PathCompletionProvider,
        )
    }
}

private object PathCompletionProvider : CompletionProvider<CompletionParameters>() {
    private val DIRECTIVE = Regex("//(SOURCES|FILES)\\s+(.*)")
    private val SOURCE_EXTENSIONS = setOf("java", "kt", "groovy", "jsh")

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val comment = parameters.position as? PsiComment ?: return
        val match = DIRECTIVE.matchEntire(comment.text) ?: return
        val directive = match.groupValues[1]
        val argumentWithDummy = match.groupValues[2]
        val argument = argumentWithDummy
            .replace(CompletionUtil.DUMMY_IDENTIFIER, "")
            .replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, "")
        // JBang //FILES mappings are target=source.
        val mappingTarget = argument.substringBeforeLast('=', "").takeIf { directive == "FILES" && '=' in argument }
        val rawPath = mappingTarget?.let { argument.substringAfterLast('=') } ?: argument
        val slash = rawPath.lastIndexOfAny(charArrayOf('/', '\\'))
        val directoryPath = if (slash >= 0) rawPath.substring(0, slash + 1) else ""
        val prefix = rawPath.substring(slash + 1)
        val currentFile = parameters.originalFile.virtualFile ?: return
        val base = currentFile.parent ?: return
        val directory = if (directoryPath.isEmpty()) base else base.findFileByRelativePath(directoryPath) ?: return
        val matcher = result.withPrefixMatcher("")

        directory.children.asSequence()
            .filter { it != currentFile && (it.isDirectory || directive == "FILES" || it.extension in SOURCE_EXTENSIONS) }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .forEach { file ->
                val name = file.name + if (file.isDirectory) "/" else ""
                val icon = if (file.isDirectory) AllIcons.Nodes.Folder else file.fileType.icon
                matcher.addElement(LookupElementBuilder.create(name).withIcon(icon).withInsertHandler { context, _ ->
                    if (mappingTarget != null) {
                        val commentStart = comment.textRange.startOffset
                        val argumentStart = commentStart + comment.text.indexOf(' ') + 1
                        context.document.replaceString(argumentStart, context.tailOffset, "$mappingTarget=$directoryPath$name")
                        context.editor.caretModel.moveToOffset(argumentStart + mappingTarget.length + 1 + directoryPath.length + name.length)
                    }
                })
            }
    }
}
