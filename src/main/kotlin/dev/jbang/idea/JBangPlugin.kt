package dev.jbang.idea

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object JBangPlugin {
    const val ID = "dev.jbang.intellij.JBangPlugin"
    const val NAME = "JBang"

    val icon16: Icon = IconLoader.getIcon("/icons/jbang-16x16.png", JBangPlugin::class.java)

    /** Directives that mark a file as a jbang root script (has its own classpath). */
    val ROOT_DIRECTIVES = setOf(
        "DEPS", "JAVA", "REPOS", "GAV", "JAVAAGENT",
        "COMPILE_OPTIONS", "JAVAC_OPTIONS", "RUNTIME_OPTIONS", "JAVA_OPTIONS",
        "NATIVE_OPTIONS", "CDS", "MANIFEST", "DESCRIPTION", "PREVIEW",
        "MAIN", "MODULE", "KOTLIN", "GROOVY",
        "NOINTEGRATIONS", "DOCS",
    )

    /** All known jbang directives (from https://www.jbang.dev/documentation/guide/latest/script-directives.html). */
    val ALL_DIRECTIVES = mapOf(
        "DEPS" to "Add Maven dependency (GAV format)",
        "REPOS" to "Additional repositories to resolve dependencies from",
        "JAVA" to "Java version to use",
        "PREVIEW" to "Enable Java preview features",
        "COMPILE_OPTIONS" to "Options passed to the compiler",
        "JAVAC_OPTIONS" to "Options passed to the compiler (alias)",
        "RUNTIME_OPTIONS" to "Options passed to the JVM at runtime",
        "JAVA_OPTIONS" to "Options passed to the JVM at runtime (alias)",
        "NATIVE_OPTIONS" to "Options passed to native-image",
        "MAIN" to "Override the main class",
        "MODULE" to "Module declaration",
        "MANIFEST" to "Entries to write to META-INF/MANIFEST.MF",
        "CDS" to "Activate Class Data Sharing",
        "JAVAAGENT" to "Activate Java agent packaging",
        "KOTLIN" to "Kotlin version to use",
        "GROOVY" to "Groovy version to use",
        "GAV" to "Set Group, Artifact and Version for this script",
        "SOURCES" to "Additional source files to include",
        "FILES" to "Mount files into the build",
        "DESCRIPTION" to "Markdown description for the script",
        "DOCS" to "Links to additional documentation resources",
        "NOINTEGRATIONS" to "Disable automatic integrations",
    )

    /** File names that are always jbang roots. */
    val BUILD_FILE_NAMES = setOf("build.jbang", "build.java", "build.kt", "build.groovy")

    /** Shebang line that marks a jbang script. */
    const val SHEBANG = "///usr/bin/env jbang"
}
