package dev.jbang.idea.run

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.RunLineMarkerProvider
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.runAnything.commands.RunAnythingCommandCustomizer
import com.intellij.ide.actions.runAnything.execution.RunAnythingRunProfile
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.javadoc.PsiSnippetAttributeList
import com.intellij.psi.javadoc.PsiSnippetDocTag
import com.intellij.psi.javadoc.PsiSnippetDocTagBody
import com.intellij.psi.javadoc.PsiSnippetDocTagValue
import com.intellij.util.execution.ParametersListUtil
import dev.jbang.idea.jbangIcon
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import java.io.File
import javax.swing.Icon

@Suppress("UnstableApiUsage")
class JavaSnippetLineMarkerProvider : RunLineMarkerProvider() {

    override fun getName(): String {
        return "Run Snippet by JBang"
    }

    override fun getIcon(): Icon {
        return jbangIcon
    }

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        if (psiElement is PsiSnippetDocTag) {
            val snippetDocTagValue = psiElement.getChildOfType<PsiSnippetDocTagValue>()
            if (snippetDocTagValue != null) {
                val snippetAttributeList = snippetDocTagValue.getChildOfType<PsiSnippetAttributeList>()
                val snippetAttributes = snippetAttributeList?.attributes
                var language = ""
                if (snippetAttributes != null && snippetAttributes.isNotEmpty()) {
                    for (attribute in snippetAttributes) {
                        if (attribute.name == "lang") {
                            language = attribute.value?.text?.trim('"') ?: ""
                        }
                    }
                }
                if (language == "java" || language == "kotlin" || language == "groovy") {
                    val snippetBody = snippetDocTagValue.getChildOfType<PsiSnippetDocTagBody>()
                    if (snippetBody != null) {
                        val testName = "Snippet"
                        return LineMarkerInfo(
                            psiElement,
                            psiElement.textRange,
                            jbangIcon,
                            { _: PsiElement? ->
                                "Run $testName"
                            },
                            { e, elt ->
                                runJavaSnippet(language, snippetBody)
                            },
                            GutterIconRenderer.Alignment.CENTER,
                            {
                                "Run $testName"
                            }
                        )
                    }
                }
            }
        }
        return null
    }

    private fun trimCode(snippetCode: String): String {
        val builder = StringBuilder()
        var lines = snippetCode.lines()
        if (lines[0].startsWith(":")) {
            lines = lines.subList(1, lines.size)
        }
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("* ")) {
                val line2 = rawLine.substring(rawLine.indexOf('*') + 1)
                if (line2.trim().startsWith("public class ")) {
                    builder.append(line2.substring(line2.indexOf(" class"))).appendLine()
                } else {
                    builder.append(line2).appendLine()
                }
            } else {
                builder.append(rawLine).appendLine()
            }
        }
        return builder.toString()
    }

    private fun runJavaSnippet(language: String, snippetDocTagBody: PsiSnippetDocTagBody) {
        val project = snippetDocTagBody.project
        val code = trimCode(snippetDocTagBody.text)
        val extName = if (language == "kotlin") {
            "kt"
        } else {
            language
        }
        val tempJavaFile = File.createTempFile("Temp", ".${extName}")
        tempJavaFile.writeText(code)
        val snippetRunCommand = "jbang ${tempJavaFile.absoluteFile}"
        tempJavaFile.deleteOnExit()
        runCommand(
            project,
            project.guessProjectDir()!!,
            snippetRunCommand,
            DefaultRunExecutor.getRunExecutorInstance(),
            SimpleDataContext.getProjectContext(project)
        )
    }

    private fun runCommand(project: Project, workDirectory: VirtualFile, commandString: String, executor: Executor, dataContext: DataContext) {
        var commandDataContext = dataContext
        commandDataContext = RunAnythingCommandCustomizer.customizeContext(commandDataContext)
        val initialCommandLine = GeneralCommandLine(ParametersListUtil.parse(commandString, false, true))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withWorkDirectory(workDirectory.path)
        val commandLine = RunAnythingCommandCustomizer.customizeCommandLine(commandDataContext, workDirectory, initialCommandLine)
        try {
            val generalCommandLine = if (Registry.`is`("run.anything.use.pty", false)) PtyCommandLine(commandLine) else commandLine
            val runAnythingRunProfile = RunSnippetProfile(generalCommandLine, commandString)
            ExecutionEnvironmentBuilder.create(project, executor, runAnythingRunProfile)
                .dataContext(commandDataContext)
                .buildAndExecute()
        } catch (e: ExecutionException) {
            Messages.showInfoMessage(project, e.message, IdeBundle.message("run.anything.console.error.title"))
        }
    }
}

class RunSnippetProfile(commandLine: GeneralCommandLine, originalCommand: String) : RunAnythingRunProfile(commandLine, originalCommand) {
    override fun getIcon(): Icon {
        return jbangIcon
    }

    override fun getName(): String {
        if (originalCommand.contains('/')) {
            return "jbang " + originalCommand.substring(originalCommand.lastIndexOf('/') + 1)
        }
        return originalCommand
    }
}