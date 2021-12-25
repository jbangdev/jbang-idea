package dev.jbang.intellij.plugins.jbang

import com.intellij.openapi.util.IconLoader


val jbangIcon = IconLoader.findIcon("icons/jbang-16x16.png")!!
val kotlinIcon = IconLoader.findIcon("icons/kotlin.svg")!!
val groovyIcon = IconLoader.findIcon("icons/groovy.svg")!!
val jshellIcon = IconLoader.findIcon("icons/jshell-16x16.png")!!

fun isJbangScriptFile(fileName: String): Boolean {
    return fileName.endsWith(".java")
            || fileName.endsWith(".kt")
            || fileName.endsWith(".jsh")
            || fileName.endsWith(".groovy")
}

fun isJbangScript(code: String): Boolean {
    return code.contains("///usr/bin/env jbang") || code.lines().any { it.startsWith("//DEPS") };
}
