package dev.jbang.intellij.plugins.jbang

import com.intellij.openapi.util.IconLoader

val jbangIcon = IconLoader.findIcon("icons/jbang-16x16.png")!!
val kotlinIcon = IconLoader.findIcon("icons/kotlin.svg")!!
val groovyIcon = IconLoader.findIcon("icons/groovy.svg")!!
val jshellIcon = IconLoader.findIcon("icons/jshell-16x16.png")!!

const val JBANG_DECLARE = "///usr/bin/env jbang"
val ALL_DIRECTIVES = listOf("JAVA", "DEPS", "GAV", "FILES", "SOURCES", "DESCRIPTION", "REPOS", "JAVAC_OPTIONS", "JAVA_OPTIONS", "JAVAAGENT", "CDS", "KOTLIN", "GROOVY")
val ALL_EXT_NAMES = listOf(".java", ".kt", ".jsh", ".groovy")

fun isJbangScriptFile(fileName: String): Boolean {
    val extName = if (fileName.contains('.')) {
        fileName.substring(fileName.lastIndexOf('.'))
    } else {
        ""
    }
    return ALL_EXT_NAMES.contains(extName)
}

fun isJbangScript(code: String): Boolean {
    return code.contains(JBANG_DECLARE) || code.lines().any { it.startsWith("//DEPS") };
}

fun isJBangDirective(line: String): Boolean {
    if (line.startsWith(JBANG_DECLARE)) return true
    if (line.startsWith("//")) {
        var directive = line.substring(2)
        if (directive.contains(' ')) {
            directive = directive.substring(0, directive.indexOf(' '))
        }
        return ALL_DIRECTIVES.contains(directive);
    }
    return false
}
