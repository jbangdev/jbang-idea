@file:Suppress("unused")

package dev.jbang.idea

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

val jbangIcon = IconLoader.getIcon("icons/jbang-16x16.png", JBangCli::class.java)
val jbangIcon12 = IconLoader.getIcon("icons/jbang-12x12.png", JBangCli::class.java)
val kotlinIcon = IconLoader.getIcon("icons/kotlin.svg", JBangCli::class.java)
val groovyIcon = IconLoader.getIcon("icons/groovy.svg", JBangCli::class.java)
val jshellIcon = IconLoader.getIcon("icons/jshell-16x16.png", JBangCli::class.java)
val mavenIcon = IconLoader.getIcon("icons/maven.svg", JBangCli::class.java)
val NOTIFICATION_GROUP_INFO = "JBang Info"
val NOTIFICATION_GROUP_SUCCESS = "JBang Success"
val NOTIFICATION_GROUP_FAILURE = "JBang Failure"

const val JBANG_DECLARE = "///usr/bin/env jbang"
const val JBANG_DECLARE_FULL = "///usr/bin/env jbang \"\$0\" \"\$@\" ; exit \$?"
val ALL_DIRECTIVES = listOf("JAVA", "DEPS", "GAV", "FILES", "SOURCES", "DESCRIPTION", "REPOS", "JAVAC_OPTIONS", "JAVA_OPTIONS", "JAVAAGENT", "CDS", "KOTLIN", "GROOVY")
val ALL_EXT_NAMES = listOf(".java", ".kt", ".jsh", ".groovy")

fun findCommandInPath(command: String): Path? {
    val path = System.getenv("PATH") ?: return null
    val pathElements = path.split(File.pathSeparator)

    for (pathElement in pathElements) {
        val commandFile = Path.of(pathElement, command)
        if (Files.exists(commandFile) && Files.isExecutable(commandFile)) {
            return commandFile
        }
    }

    return null
}
fun getJBangCmdAbsolutionPath(): String {
    val userHome = System.getProperty("user.home")
    val jbangHome = System.getenv("JBANG_HOME")
    val jbangScript = if (SystemInfo.isWindows) "jbang.cmd" else "jbang"
    var actualJBangScript: Path?

    if(jbangHome!=null) {
        actualJBangScript = Path.of(jbangHome, "bin/$jbangScript")
    } else {
        actualJBangScript = findCommandInPath(jbangScript)
    }

    if(actualJBangScript==null) {
        actualJBangScript = Path.of(userHome, ".jbang/bin/$jbangScript")
    }

    return actualJBangScript?.toAbsolutePath().toString()
}


fun isJBangScriptFile(fileName: String): Boolean {
    val extName = if (fileName.contains('.')) {
        fileName.substring(fileName.lastIndexOf('.'))
    } else {
        ""
    }
    return ALL_EXT_NAMES.contains(extName)
}

fun isJBangScript(code: CharSequence): Boolean {
    return code.contains(JBANG_DECLARE) || code.lines().any { it.startsWith("//DEPS") }
}

fun getJBangDirective(line: String): String? {
    if (line.startsWith(JBANG_DECLARE)) return JBANG_DECLARE
    if (line.startsWith("//")) {
        var directive = line.substring(2)
        if (directive.contains(' ')) {
            directive = directive.substring(0, directive.indexOf(' '))
        }
        if (ALL_DIRECTIVES.contains(directive)) {
            return directive
        }
    }
    return null
}
