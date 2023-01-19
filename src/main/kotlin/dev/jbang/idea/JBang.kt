@file:Suppress("unused")

package dev.jbang.idea

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import java.io.File

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

fun getJBangCmdAbsolutionPath(): String {
    val userHome = System.getProperty("user.home")
    return if (SystemInfo.isWindows) {
        if (File(System.getenv("JBANG_HOME") ?: "", "bin/jbang.cmd").exists()) {
            File(System.getenv("JBANG_HOME") ?: "", "bin/jbang.cmd").absolutePath
        } else if (File(userHome, ".sdkman/candidates/jbang/current/bin/jbang.cmd").exists()) {
            File(userHome, ".sdkman/candidates/jbang/current/bin/jbang.cmd").absolutePath
        } else {
            File(userHome, ".jbang/bin/jbang.cmd").absolutePath
        }
    } else {
        if (File(System.getenv("JBANG_HOME") ?: "", "bin/jbang").exists()) {
            File(System.getenv("JBANG_HOME") ?: "", "bin/jbang").absolutePath
        } else if (File("/usr/local/bin/jbang").exists()) {
            "/usr/local/bin/jbang"
        } else if (File(userHome, ".sdkman/candidates/jbang/current/bin/jbang").exists()) {
            File(userHome, ".sdkman/candidates/jbang/current/bin/jbang").absolutePath
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
