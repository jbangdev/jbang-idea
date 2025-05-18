jbang-idea-plugin
======================
[![Build](https://github.com/maxandersen/jbang-idea/actions/workflows/build.yml/badge.svg)](https://github.com/maxandersen/jbang-idea/actions/workflows/build.yml)
[![Version](https://img.shields.io/jetbrains/plugin/v/18257.svg)](https://plugins.jetbrains.com/plugin/18257)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/18257.svg)](https://plugins.jetbrains.com/plugin/18257)

<!-- Plugin description -->
**JBang plugin** is a plugin for IntelliJ IDEA to integrate [JBang](https://www.jbang.dev/).

The following features are available:
              
* JSON Schema for jbang-catalog.json and code completion for `script-ref`
* JDKs sync with JBang: sync JDKs from JBang to IntelliJ IDEA
* JBang script creation from file templates: New -> JBang Script
* JBang directives completion:  for example `//DEPS`, `//SOURCES`
* Sync Dependencies between JBang and Gradle
* Sync Dependencies to IDEA's module when using `idea .` to open JBang project
* JBang Run Line Marker for `///usr/bin/env jbang`
* Java scratch file support
* Run Configuration support: run JBang script by right click
    * file name end with '.java', '.kt', '.groovy' or '.jsh'
    * file code should contain `///usr/bin/env jbang` or `//DEPS`
* GAV completion for `//DEPS `
    * text without colon - full text search `google.guava`, and words seperated by `.` or `-`
    * text with one colon - artifact search based on groupId `com.google.guava:`
    * text with two colons - version search based on groupId and artifactId `com.google.guava:guava:`

<!-- Plugin description end -->

## Sync Dependencies between JBang and Gradle

Right lick JBang script and Choose `Sync JBang DEPS` and sync dependencies between JBang script and build.gradle.

**Limitations**:

* Gradle Groovy support now: only detect `build.gradle`
* After sync, and you need to click `Refresh` floating button if without Auto-Reloading enabled 
* Dependency remove detection: if you want to delete dependencies, and you should delete lines in build.gradle and script file by hand. 

## Install

<kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "jbang"</kbd> > <kbd>Install Plugin</kbd>  > <kbd>Restart IntelliJ IDEA</kbd>

## Build

```
$ # JDK 11 required
$ ./gradlew -x test patchPluginXml buildPlugin
```

<kbd>Preferences</kbd> > <kbd>Plugins</kbd> >  <kbd>Gear Icon Right Click</kbd> > <kbd>Install Plugin from Disk</kbd> > <kbd>Choose
$PROJECT_DIR/build/distributions/jbang-idea-plugin-0.x.0.zip</kbd>  > <kbd>Restart IntelliJ IDEA</kbd>
