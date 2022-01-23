@file:Suppress("unused")

package dev.jbang.idea

class ScriptInfo {
    var originalResource: String? = null
    var backingResource: String? = null
    var applicationJar: String? = null
    var mainClass: String? = null
    var dependencies: List<String>? = null
    var repositories: List<Repo>? = null
    var resolvedDependencies: List<String>? = null
    var javaVersion: String? = null
    var requestedJavaVersion: String? = null
    var availableJdkPath: String? = null
    var compileOptions: List<String>? = null
    var runtimeOptions: List<String>? = null
    var files: List<ResourceFile>? = null
    var sources: List<ScriptInfo>? = null
    var description: String? = null
    var gav: String? = null
}

class Repo {
    var id: String? = null
    var url: String? = null
}

class ResourceFile {
    var originalResource: String? = null
    var backingResource: String? = null
    var target: String? = null
}