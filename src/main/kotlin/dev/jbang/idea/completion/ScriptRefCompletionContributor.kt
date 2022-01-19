package dev.jbang.idea.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.json.JsonElementTypes
import com.intellij.json.JsonLanguage
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.util.ProcessingContext
import dev.jbang.idea.isJBangScriptFile
import java.nio.file.Path


class ScriptRefCompletionContributor : CompletionContributor(), DumbAware {
    companion object {
        val scriptRefValueCapture = psiElement().afterLeaf(psiElement(JsonElementTypes.COLON).afterLeaf("\"script-ref\""))
            .withSuperParent(2, JsonProperty::class.java)
            .withLanguage(JsonLanguage.INSTANCE)
            .inFile(psiFile().withName("jbang-catalog.json"))
    }

    init {
        extend(
            CompletionType.BASIC, scriptRefValueCapture,
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                    val jsonStringLiteral = parameters.position.parent as JsonStringLiteral
                    var path = StringUtil.trim(jsonStringLiteral.text.trim('"').replace(CompletionUtil.DUMMY_IDENTIFIER, "").replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, ""))
                    val jbangCatalogJsonFile = parameters.originalFile
                    if (jbangCatalogJsonFile.name == "jbang-catalog.json"
                        && !(path.startsWith("http://") || path.startsWith("https://"))
                    ) {
                        val jsonFileDirectory = jbangCatalogJsonFile.parent!!
                        val naviDirectory = if (path.isEmpty()) {
                            jsonFileDirectory.virtualFile
                        } else {
                            val realPath = if (path.startsWith("/")) {
                                Path.of(path)
                            } else {
                                jsonFileDirectory.virtualFile.toNioPath().resolve(path)
                            }
                            VirtualFileManager.getInstance().findFileByNioPath(realPath)
                        }
                        if (naviDirectory != null && naviDirectory.isDirectory) {
                            if (path.isNotEmpty() && !path.endsWith("/")) {
                                path += "/"
                            }
                            val items = naviDirectory.children
                                .filter {
                                    val filePathName = it.name
                                    if (!filePathName.startsWith(".")) {
                                        it.isDirectory || isJBangScriptFile(filePathName)
                                    } else {
                                        false
                                    }
                                }
                                .map {
                                    if (it.isDirectory) {
                                        it.name + "/"
                                    } else {
                                        it.name
                                    }
                                }
                                .map {
                                    LookupElementBuilder.create(path + it)
                                }
                            result.addAllElements(items)
                        }
                    }
                }
            }
        )
    }


}
