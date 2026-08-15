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
        "COMPILE_OPTIONS", "RUNTIME_OPTIONS", "NATIVE_OPTIONS",
        "CDS", "MANIFEST", "DESCRIPTION", "PREVIEW"
    )

    /** All known jbang directives. */
    val ALL_DIRECTIVES = mapOf(
        "JAVA" to "Java version to use",
        "DEPS" to "Add dependency (GAV format)",
        "GAV" to "Set Group, Artifact and Version for this script",
        "SOURCES" to "Pattern to include as source files",
        "FILES" to "Mount files into the build",
        "REPOS" to "Repositories to resolve dependencies from",
        "DESCRIPTION" to "Markdown description for the script",
        "COMPILE_OPTIONS" to "Options passed to javac",
        "RUNTIME_OPTIONS" to "Options passed to the JVM at runtime",
        "NATIVE_OPTIONS" to "Options passed to native-image",
        "MANIFEST" to "Entries to write to META-INF/MANIFEST.MF",
        "JAVAAGENT" to "Activate Java agent packaging",
        "CDS" to "Activate Class Data Sharing",
        "PREVIEW" to "Enable Java preview features",
    )

    /** File names that are always jbang roots. */
    val BUILD_FILE_NAMES = setOf("build.jbang", "build.java", "build.kt", "build.groovy")

    /** Shebang line that marks a jbang script. */
    const val SHEBANG = "///usr/bin/env jbang"
}
