# Contributing to the JBang IntelliJ plugin

Thanks for helping improve the plugin. This project follows a strict test-first
workflow.

## Test-first workflow

Every behavioral change starts with a failing test:

1. Write a test that reproduces the bug or specifies the feature.
2. Run it and confirm it fails.
3. Implement the smallest change that makes it pass.
4. Run the targeted test, then the full suite.
5. Update `CHANGELOG.md` under `[Unreleased]` for any user-visible change.

Every user-facing feature must have at least one test that verifies the
user-visible contract (gutter icon, action registration, completion results,
run configuration discovery), not only an internal method.

## Build and test

```sh
./gradlew test                                   # run all tests
./gradlew test --tests "dev.jbang.idea.run.*"    # run specific tests
./gradlew runIde                                 # launch a sandbox IDE
./gradlew buildPlugin                            # build the distributable zip
./gradlew verifyPlugin --args='-mute TemplateWordInPluginId'  # compatibility check
```

Building the plugin requires JDK 25. The plugin targets IntelliJ IDEA 2026.2+.

## Compatibility with new IntelliJ versions

New IntelliJ versions can break the plugin in two ways: removed or changed APIs (binary breaks,
e.g. #155) and changed platform behavior (e.g. #165, #180). CI checks both:

| Check | When | IntelliJ versions |
|---|---|---|
| `test` (`build.yml`) | every PR and push | `platformVersion` (oldest supported line), Linux/Windows/macOS |
| `testLatestIde` (`build.yml`) | every PR and push | newest stable release, Linux |
| `verifyPlugin` (`build.yml`) | every PR and push | newest build of every line ≥ `pluginSinceBuild`, EAPs included (binary checks only) |
| `compatibility.yml` | every Monday, or by hand from the Actions tab | newest build of every line ≥ `pluginSinceBuild`, EAPs included |

`.github/scripts/intellij-versions.sh` lists the versions to test from the JetBrains release API.
To run the tests against another version locally:

```sh
./gradlew test -PplatformVersion=2026.3        # a release
./gradlew test -PplatformVersion=263.7307.44   # an EAP, by build number
```

When `compatibility.yml` fails, it opens an issue named
`IntelliJ IDEA <version> (<build>): tests fail` and comments on it in later failing runs. Fix it
like any bug: reproduce with the command above, write a failing test, fix, and close the issue.

## IntelliJ platform conventions

- Override `getOptionsClass()` on run configurations.
- Register a `ProgramRunner` for every supported executor ID.
- `AdditionalLibraryRootsListener.fireAdditionalLibraryChanged()` needs write
  access on EDT.
- Call terminal-widget operations on the correct thread (`createNewSession` on
  EDT, `hasRunningCommands` off EDT).
- Handle both `PsiComment` and `PsiDocComment` when walking JBang files, because
  the shebang parses as Javadoc.

See [AGENTS.md](AGENTS.md) for the full set of conventions and architecture
notes, and [docs](docs/modules/ROOT/pages/architecture.adoc) for the
integration model.

## Documentation

User-visible changes must update the AsciiDoc pages under
`docs/modules/ROOT/pages/` and any relevant screenshots.
