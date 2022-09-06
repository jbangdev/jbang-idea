<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# jbang-idea-plugin Changelog

## [Unreleased]

## [0.22.0]

- Added: new `//MANIFEST` keyword to allow writing entries to `META-INF/manifest.mf`
- Added: `JBANG_HOME` environment variable support
- Added: icon for `build.jbang`

## [0.21.0]

- Added: JBang icon for build.java, build.kt and build.groovy files
- Added: JBang live templates
    * jbang: generate JBang declaration
    * jbang-build: generate JBang declaration with build info
    * jbang-sb: generate JBang declaration with Spring Boot dependencies

## [0.20.0]

- Added: Java 18 Snippet support with `java`, `groovy`, `kotlin` lang attribute

```
/**
 * {@snippet lang = java:
 * public class Demo {
 *    public static void main(String[] args) {
 *         System.out.println("Hello Snippet!");
 *     }
 * }
 *}
 */
public class SnippetApp {
}
```

## [0.19.0]

- Added: Catalog alias support for JBang run configuration
- Added: environment variables support for JBang run configuration
- Fixed: Force to refresh script info when click refresh button in JBang tool window

## [0.18.0]

- Added: code completion/navigation for //SOURCES
- Added: Java version synced within module
- Fixed: external library name always as jbang, and now is `${moduelName}-jbang`

## [0.17.0]

- Added: JBang ToolWindow listener to make load JBang script info automatically
- Added: open new JBang script files after creation from template
- Fix: save all documents when to sync DEPS

## [0.17.0]

- Added: JBang ToolWindow listener to make load JBang script info automatically
- Added: open new JBang script files after creation from template
- Fix: save all documents when to sync DEPS

## [0.16.0]

- Added: introduce zt-exec to call JBang command
- Added: introduce ProgressManager and Task.Backgroundable to sync dependencies asynchronously
- Fix: added descriptions to directive completions

## [0.15.0]

- Added: Support 2022.1 EAP

## [0.13.0]

- GAV completion with last version support

## [0.12.0]

- Bug fix: remove file editor listener because of performance

## [0.11.0]

### Added

- Code completion and navigation for `script-ref` in `jbang-catalog.json`
- GAV completion for `//DEPS `
- text without colon - full text search `google.guava`, and words seperated by `.` or `-`
- text with one colon - artifact search based on groupId `com.google.guava:`
- text with two colons - version search based on groupId and artifactId `com.google.guava:guava:`

## [0.10.0]

### Added

- JBang module wizard: create new JBang project or create JBang module on current project
- Language detection for JBang module creation: create different script file based on Java/Groovy/Kotlin chosen

## [0.9.0]

### Added

- Module JDK sync according to `//JAVA`
- Remove bundle of jbang.jar
- Dependencies sync adjusted to one way: from Gradle to DEPS or from DEPS to Gradle
- High lighter for JBang directives
- JBang tool window

## [0.6.0]

### Added

- Add create script from JBang template
- Move all DEPS to module's jbang library

## [0.5.0]

### Added

- Add to sync DEPS to IDEA's module:  use `idea .` to open JBang project

## [0.4.0]

### Added

- JBang Run Line Marker for `///usr/bin/env jbang`
- `//GROOVY` directive completion for JBang Groovy script

## [0.3.0]

### Added

- Sync Dependencies Action: right click script file and sync dependencies between JBang and Gradle
- Add icon for `JBang run` in editor popup menu
- Append ` by JBang` to JBang run configuration to indicate it run by JBang

## [0.2.0]

### Added

- GAV directive added for completion
- Run configuration for Groovy: run Groovy by JBang

## [0.1.0]

### Added

- JDK sync from JBang to IntelliJ IDEA
- Json Schema support for jbang-catalog.json
- Run Configuration support: run JBang script by right click
- JBang script creation from file templates: New -> JBang Script
- JBang directives completion:  for example `//DEPS`, `//SOURCES`