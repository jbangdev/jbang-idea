# JBang IntelliJ Plugin

[![Build](https://github.com/jbangdev/jbang-idea/actions/workflows/build.yml/badge.svg)](https://github.com/jbangdev/jbang-idea/actions/workflows/build.yml)
[![Version](https://img.shields.io/jetbrains/plugin/v/18257.svg)](https://plugins.jetbrains.com/plugin/18257)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/18257.svg)](https://plugins.jetbrains.com/plugin/18257)

> **0.100.0 is a [complete rewrite](#version-01000--complete-rewrite)** of the plugin - that makes JBang much more of a first class natural citizen in Intellij. 

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

## Getting Started

1. Install [JBang](https://www.jbang.dev/download) (or let the plugin install it from **Settings > Tools > JBang**)
2. Install the plugin: **Settings > Plugins > Marketplace** → search "JBang" → **Install** → restart
3. Open any folder containing `.java`, `.kt`, `.groovy`, `.jsh`, or `.jbang` files with JBang directives
4. The plugin detects root scripts, syncs dependencies, and you're ready to code

No project wizard or module setup required — just open a folder.

## Requirements

* IntelliJ IDEA 2026.2 or newer
* JBang CLI installed (the plugin can install it for you)
* JDK 25+ for building the plugin from source

## Version 0.100.0 — Complete Rewrite

Version 0.100.0 is a ground-up rewrite of the plugin. The old plugin (≤ 0.26) mutated IntelliJ modules, mirrored dependencies into Gradle, and used a custom tool window. The new plugin:

* Uses `jbang info tools` as the single source of truth — no module mutation, no Gradle mirroring
* Overlays dependencies as synthetic libraries — existing Gradle/Maven projects are never touched
* Supports multiple root scripts with isolated classpaths and JDKs
* Adds Debug support, terminal execution, WSL support, and detailed diagnostics
* Replaces the tool window with a status bar widget and External Libraries integration

See [CHANGELOG.md](CHANGELOG.md) for the full list of additions and removals.

## Documentation

Full documentation with screenshots: [jbang.dev/documentation/jbang-idea](https://www.jbang.dev/documentation/jbang-idea/latest/index.html)

## How It Works

The plugin treats the JBang CLI as the single source of truth:

* **Sync** — runs `jbang info tools --quiet` and exposes resolved JARs and declared sources as synthetic libraries under *External Libraries*. Gradle and Maven models are never touched.
* **Multi-root** — each root script (`//DEPS`, `//JAVA`, etc.) keeps its own classpath, sources, and JDK. The status bar widget switches between roots.
* **Run & Debug** — gutter icons, right-click actions, and run configurations. Debug auto-attaches via JDWP. Runs in the Terminal by default.
* **Completion** — directives, Maven coordinates (local repo + Maven Central), `//SOURCES`/`//FILES` paths, and catalog `script-ref`.
* **Diagnostics** — unknown directives, malformed GAV, duplicate `//DEPS`, unresolved dependencies, and missing resources highlighted on the exact token.

## Install from Marketplace

<kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > search "JBang" > <kbd>Install</kbd> > restart

## Build from Source

```sh
./gradlew test buildPlugin
```

The plugin zip is at `build/distributions/jbang-idea-plugin-*.zip`.

Install it via <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>⚙</kbd> > <kbd>Install Plugin from Disk</kbd>.

## Contributing

See [AGENTS.md](AGENTS.md) for test-first workflow, IntelliJ platform conventions, and architecture notes.

```sh
./gradlew test                              # run all tests
./gradlew test --tests "dev.jbang.idea.*"   # run specific tests
./gradlew runIde                            # launch sandboxed IDE with plugin
./gradlew buildPlugin                       # build distributable zip
```
