jbang-idea-plugin
======================
[![Build](https://github.com/maxandersen/jbang-idea/actions/workflows/build.yml/badge.svg)](https://github.com/maxandersen/jbang-idea/actions/workflows/build.yml)
[![Version](https://img.shields.io/jetbrains/plugin/v/18257.svg)](https://plugins.jetbrains.com/plugin/18257)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/18257.svg)](https://plugins.jetbrains.com/plugin/18257)

<!-- Plugin description -->
**JBang plugin** integrates [JBang](https://www.jbang.dev/) scripts into IntelliJ IDEA without modifying the project module model.

Features:

* Automatic classpath and source synchronization from `jbang info tools`
* Multiple JBang roots with isolated classpaths and active-root switching
* Per-script `//JAVA` JDK registration and selection for standalone projects
* Run and Debug configurations, gutter markers, and editor/project context actions
* Completion for directives, Maven coordinates, `//SOURCES`, `//FILES`, and catalog `script-ref`
* Navigation for local sources, files, and catalog scripts
* Exact diagnostics for invalid directives, unresolved dependencies, and missing resources
* Explicit **Sync JBang Project** action with progress and detailed results
* JSON Schema support for `jbang-catalog.json`
* CLI-backed **New → JBang Script** templates

<!-- Plugin description end -->

## Documentation

Full documentation is [here](https://www.jbang.dev/documentation/jbang-idea/latest/index.html).

## How synchronization works

The plugin treats the JBang CLI as the source of truth. It resolves scripts after opening and saving them, or when **Sync JBang Project** is selected. Resolved dependencies are exposed as synthetic libraries, so Gradle and Maven project models are left unchanged.

## Install

<kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "jbang"</kbd> > <kbd>Install Plugin</kbd>  > <kbd>Restart IntelliJ IDEA</kbd>

## Build

```
$ # JDK 25 required for IntelliJ IDEA 2026.2
$ ./gradlew test buildPlugin
```

or if using just:

```
$ just build
```

<kbd>Preferences</kbd> > <kbd>Plugins</kbd> >  <kbd>Gear Icon Right Click</kbd> > <kbd>Install Plugin from Disk</kbd> > <kbd>Choose
$PROJECT_DIR/build/distributions/jbang-idea-plugin-0.x.0.zip</kbd>  > <kbd>Restart IntelliJ IDEA</kbd>
