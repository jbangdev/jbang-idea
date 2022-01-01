package dev.jbang.idea

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import java.io.File

val jbangIcon = IconLoader.findIcon("icons/jbang-16x16.png")!!
val kotlinIcon = IconLoader.findIcon("icons/kotlin.svg")!!
val groovyIcon = IconLoader.findIcon("icons/groovy.svg")!!
val jshellIcon = IconLoader.findIcon("icons/jshell-16x16.png")!!

const val JBANG_DECLARE = "///usr/bin/env jbang"
const val JBANG_DECLARE_FULL = "///usr/bin/env jbang \"\$0\" \"\$@\" ; exit \$?"
val ALL_DIRECTIVES = listOf("JAVA", "DEPS", "GAV", "FILES", "SOURCES", "DESCRIPTION", "REPOS", "JAVAC_OPTIONS", "JAVA_OPTIONS", "JAVAAGENT", "CDS", "KOTLIN", "GROOVY")
val ALL_EXT_NAMES = listOf(".java", ".kt", ".jsh", ".groovy")

fun getJBangCmdAbsolutionPath(): String {
    val userHome = System.getProperty("user.home")
    return if (SystemInfo.isWindows) {
        File(userHome, ".jbang/bin/jbang.cmd").absolutePath
    } else {
        if (File("/usr/local/bin/jbang").exists()) {
            "/usr/local/bin/jbang"
        } else {
            File(userHome, ".jbang/bin/jbang").absolutePath
        }
    }
}

fun isJBangScriptFile(fileName: String): Boolean {
    val extName = if (fileName.contains('.')) {
        fileName.substring(fileName.lastIndexOf('.'))
    } else {
        ""
    }
    return ALL_EXT_NAMES.contains(extName)
}

fun isJBangScript(code: String): Boolean {
    return code.contains(JBANG_DECLARE) || code.lines().any { it.startsWith("//DEPS") };
}

fun getJBangDirective(line: String): String? {
    if (line.startsWith(JBANG_DECLARE)) return "///usr/bin/env jbang"
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
