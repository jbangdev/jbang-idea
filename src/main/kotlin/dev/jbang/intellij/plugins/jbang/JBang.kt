package dev.jbang.intellij.plugins.jbang

import com.intellij.openapi.util.IconLoader


val jbangIcon = IconLoader.findIcon("icons/jbang-16x16.png")!!
val kotlinIcon = IconLoader.findIcon("icons/kotlin.svg")!!
val groovyIcon = IconLoader.findIcon("icons/groovy.svg")!!
val jshellIcon = IconLoader.findIcon("icons/jshell-16x16.png")!!

val ALL_DIRECTIVES = listOf("JAVA", "DEPS", "GAV", "FILES", "SOURCES", "DESCRIPTION", "REPOS", "JAVAC_OPTIONS", "JAVA_OPTIONS", "JAVAAGENT", "CDS", "KOTLIN", "GROOVY")

fun isJbangScriptFile(fileName: String): Boolean {
    return fileName.endsWith(".java")
            || fileName.endsWith(".kt")
            || fileName.endsWith(".jsh")
            || fileName.endsWith(".groovy")
}

fun isJbangScript(code: String): Boolean {
    return code.contains("///usr/bin/env jbang") || code.lines().any { it.startsWith("//DEPS") };
}

fun isJBangDirective(line: String): Boolean {
    if (line.startsWith("///usr/bin/env jbang")) return true
    if (line.startsWith("//")) {
        var directive = line.substring(2)
        if (directive.contains(' ')) {
            directive = directive.substring(0, directive.indexOf(' '))
        }
        return ALL_DIRECTIVES.contains(directive);
    }
    return false
}
