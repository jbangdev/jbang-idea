<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# jbang-idea-plugin Changelog

## [Unreleased]

### Fixed
- Run gutter icon now appears reliably on Kotlin scripts. The marker only recognized
  Java's doc-comment PSI type for a leading `///` shebang; it now checks the
  cross-language `PsiDocCommentBase` interface so a doc-comment-like leading
  comment is handled consistently in Kotlin (and any other language) as well
  ([#174](https://github.com/jbangdev/jbang-idea/issues/174)).

## [0.102.0]

### Added
- The New JBang Script dialog now has a Copy button beside the generated `jbang init`
  command preview.
- Directive-argument completion for `//JAVA` versions, `//REPOS` shortcuts, and `//MAIN`
  classes, plus multi-value completion for additional `//DEPS`, `//SOURCES`, and `//FILES`
  entries on the same directive.
- Quick fixes to uppercase a mis-cased directive and to remove a duplicate `//DEPS` entry.
- JShell (`.jsh`) scripts now receive directive and directive-argument completion.
- Settings now link to the documentation and issue tracker.
- Troubleshooting, known-limitations, and architecture documentation pages, plus a
  `CONTRIBUTING.md`.

### Changed
- New Script creation validates the destination and no longer overwrites files silently:
  existing files, absolute paths, and paths escaping the selected directory are rejected,
  while nested destinations are created and opened.
- Run/Debug configurations created from context are matched by script path (not just name)
  and created as temporary configurations, so same-named scripts in different directories no
  longer collide.
- Script detection now only recognises directives in the initial comment block at column
  zero, matching JBang, and consistently scans the first 200 lines.
- The catalog JSON schema accepts current Java versions and template properties.
- Feature tips link to the published documentation instead of GitHub source.

### Fixed
- JBang auto-detection now uses IntelliJ's console/shell environment PATH before
  the IDE process PATH, so installs from tools such as SDKMAN are found when
  IntelliJ was launched outside that shell.
- Persisted JBang settings now read and write the loaded state correctly after
  IDE restart.
- Run configurations validate the script, working directory, and JBang availability before
  launching.

## [0.101.0]

### Added

- Template creation now displays declared template properties in an IntelliJ-style key/value
  table, resolves free-form catalog-qualified template IDs, reports the exact resolved template
  and catalog, and passes user overrides to `jbang init` ([#46](https://github.com/jbangdev/jbang-idea/issues/46)).

### Changed

- Run/Debug configurations now use IntelliJ's standard parameter controls, including macro
  expansion, structured environment variables, parent-environment selection, and consistent
  options and working-directory handling in terminal mode.
- New Script dialog shows a live command preview at the bottom so users can see the
  exact `jbang init` command that will be executed.
- CLI commands are now logged at INFO level (visible in `idea.log` without enabling debug
  logging), making it easier to troubleshoot sync and init issues.

### Fixed

- Windows: `CreateProcess error=193` when running scripts — the plugin resolved to the
  extensionless `jbang` bash script instead of `jbang.cmd` in `~/.jbang/bin` and `JBANG_HOME`.
- Kotlin-compiled dependencies now resolve in standalone JBang scripts under Kotlin K2 mode.
  The active script's Kotlin JARs are mirrored to a module library only when required; Java-only
  JBang dependencies remain isolated per script. The support library is named after its relevant
  script instead of using the internal “active root” terminology ([#165](https://github.com/jbangdev/jbang-idea/issues/165)).
- Run gutter icon now appears on Kotlin class names and `fun main()` declarations, not just
  on directive comments. Previously only Java PSI types were handled ([#164](https://github.com/jbangdev/jbang-idea/issues/164)).
- Fix crash when syncing from the status bar widget: `saveDocument` was called outside
  `WriteIntentReadAction`, causing a threading assertion on EDT ([#166](https://github.com/jbangdev/jbang-idea/issues/166)).
- Avoid evaluating run-marker tooltips on the EDT when positioning feature tips, which could
  trigger IntelliJ's "Slow operations are prohibited on EDT" assertion.

## [0.100.0]

Complete rewrite of the JBang IntelliJ plugin. The plugin now uses `jbang info tools` as its
single source of truth and overlays dependencies as synthetic libraries — Gradle, Maven, and
IntelliJ module models are never modified.

### Added

- Multi-root classpath isolation: each JBang root script keeps its own resolved dependencies,
  declared sources, and requested Java version.
- Active-root switching via the status bar widget, with an option to open the selected root file.
- `//SOURCES` ownership and element finder: declared sources resolve in the editor and inherit
  the owning root's classpath. Sibling sources from other roots remain isolated.
- Run **and** Debug support with JDWP auto-attach, gutter icons, context menu actions, and
  terminal execution mode (with Windows PTY fallback).
- Expanded run configuration: JBang options, quoted arguments, environment variables, and
  working directory.
- Local and remote Maven coordinate completion for `//DEPS`, with cancellable time-bounded
  central lookups and subdued `local`/`remote`/`snapshot` presentation.
- Path completion and Ctrl/Cmd-click navigation for `//SOURCES` and `//FILES` (including
  `target=source` mappings and multiple entries per directive).
- Catalog `script-ref` completion and navigation in `jbang-catalog.json`.
- Exact diagnostics: unknown directives, malformed coordinates, duplicate `//DEPS`, unresolved
  dependencies, and missing `//SOURCES`/`//FILES` resources highlighted on the failing token.
- Visible synchronization: `syncing…`, `(synced)`, and `(sync failed: N errors)` in the status
  bar; detailed errors in tooltips and notifications; explicit **Sync JBang Project** action with
  automatic save.
- Per-root `//JAVA` JDK registration and standalone project SDK assignment (Gradle/Maven SDKs
  are never overwritten).
- CLI-backed **New → JBang Script** with smart filename suggestions from `jbang template list`.
- Own `.jbang` file type (directive-only, not parsed as Java).
- `.jsh` registered as JShell.
- JSON Schema for `jbang-catalog.json`.
- Navigate from External Libraries back to the owning root script (F4/Enter).
- All 19 official JBang directives plus `JAVAC_OPTIONS` and `JAVA_OPTIONS` aliases.
- Settings: JBang path, auto-sync toggle, and root-open prompt with "Do not ask again" support.
- Cross-platform CI: tests on Linux, Windows, and macOS; Plugin Verifier in the build pipeline.
- Documentation with full screenshot walkthrough and contract tests.

### Changed

- Synchronization is now overlay-based: dependencies appear under External Libraries as synthetic
  JBang libraries. No module dependencies, Gradle mirroring, or project-model mutation.
- Run/Debug context actions are always visible for JBang scripts (not hidden inside source roots).

### Removed

- Module builder/wizard — just open a folder containing JBang scripts.
- Gradle dependency mirroring (`jbang-withGradle.xml` was empty).
- `DependencyModifier` (was `return false` — never functional).
- Tool window with dependency list — replaced by status bar widget and External Libraries.
- File-based index — replaced by in-memory root cache.
- `JavaSnippetLineMarkerProvider` (ran `@snippet` Javadoc tags via JBang).
- Live templates (`jbang`, `jbang-build`, `jbang-sb`) — replaced by CLI-backed templates.

## [0.26.1] - 2026-07-26

**Full Changelog**: https://github.com/jbangdev/jbang-idea/compare/v0.26.0...v0.26.1

## [0.25.2]

- Fix: Allow comments in jbang-catalog.json files
- Fix range problem: https://github.com/jbangdev/jbang-idea/issues/143
- Use backgroundPostStartupActivity to sync JDKs
- Trigger JBang script creation from PROJECT_VIEW only

## [0.25.1]

- Feat: Compatible with IntelliJ IDEA 2023.2+
- Feat: Add JBang Path in settings
- Feat: Allow install into IntelliJ IDEA EAP
- Fix: Select proper directory when creating script

[Unreleased]: https://github.com/jbangdev/jbang-idea/compare/v0.101.0...HEAD
[0.101.0]: https://github.com/jbangdev/jbang-idea/compare/v0.100.0...v0.101.0
[0.100.0]: https://github.com/jbangdev/jbang-idea/compare/v0.26.1...v0.100.0
[0.26.1]: https://github.com/jbangdev/jbang-idea/compare/v0.25.2...v0.26.1
[0.25.2]: https://github.com/jbangdev/jbang-idea/compare/v0.25.1...v0.25.2
[0.25.1]: https://github.com/jbangdev/jbang-idea/commits/v0.25.1
