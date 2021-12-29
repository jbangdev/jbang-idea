package dev.jbang.intellij.plugins.jbang

data class ScriptInfo(
    val originalResource: String,
    val backingResource: String,
    val applicationJar: String,
    val dependencies: List<String>,
    val resolvedDependencies: List<String>,
    val requestedJavaVersion: String,
    val availableJdkPath: String,
)