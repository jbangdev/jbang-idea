# JBang IntelliJ Plugin Improvement Plan

Implement this work as small, independently reviewable PRs. Every behavioral change starts with a failing test.

## Status

- PR 1 Safe script creation — **done** (destination validation, no silent overwrite, nested create/open).
- PR 2 Run/Debug identity + validation — **done** (path-based reuse, temporary configs, `checkConfiguration()`). Gutter/context execute-path test still worth adding.
- PR 3 Script detection — **done** (column-zero directives, initial comment block only, consistent 200-line scan).
- PR 4 Directive editing — **done** (multi-value `//DEPS`/`//SOURCES`/`//FILES`, `//JAVA`/`//REPOS`/`//MAIN` args, uppercase + duplicate quick fixes).
- PR 5 Language support — **partial** (JShell directive-argument completion added and documented; full per-language matrix still open).
- PR 6 Catalog schema — **done** (current Java versions + template properties; bundled-schema test).
- PR 7 Lifecycle/cancellation — **done** (project-scoped cancellable `JBangProjectService.scope`). Stale-result and notification tests still open.
- PR 8 UI polish — **partial** (settings Documentation/Report links, ActionLink download, feature tips point to published docs). Native Run/Debug icons still open.
- PR 9 Documentation — **done** (architecture, troubleshooting, known-limitations pages; CONTRIBUTING.md; `.DS_Store` ignore; DocumentationTest extended).
- PR 10 CI cleanup — **partial** (wrapper validation, `$GITHUB_OUTPUT`, removed fake UI-tests workflow and unused qodana.yml). Linux CLI smoke test and deprecated-API cleanup still open.


## Rules for every PR

1. Add a failing test reproducing the missing behavior.
2. Run it and confirm the expected failure.
3. Implement the smallest fix.
4. Run the targeted test.
5. Run `./gradlew test`.
6. Run `./gradlew verifyPlugin --args='-mute TemplateWordInPluginId'` for API or registration changes.
7. Update `[Unreleased]` in `CHANGELOG.md` for user-visible changes.
8. Add at least one test through IntelliJ's public UI/API wiring, not only internal helpers.

## PR 1 — Make New Script creation safe

### Changes

- Reject blank, invalid, absolute, and escaping paths.
- Detect an existing destination before running JBang.
- Remove unconditional silent overwrite or require explicit confirmation.
- Support nested destination paths and open the created file.
- Show template-loading failures with Retry.
- Keep the command preview and Copy button synchronized with validation.

### Tests

- Existing file is not modified.
- `../outside.java` and absolute paths are rejected.
- Nested `examples/Hello.java` is created and opened.
- Overwrite proceeds only after explicit confirmation.
- Template lookup failure is visible and Retry invokes the resolver again.
- Copy copies the exact displayed command.
- Copy is disabled when the command is invalid.
- A UI test finds validation text and buttons in the actual dialog.

## PR 2 — Correct Run/Debug configuration behavior

### Changes

- Match generated configurations by type and canonical script path, never display name alone.
- Give scripts with the same filename separate configurations.
- Make context-generated configurations temporary unless explicitly saved.
- Add `checkConfiguration()` validation for missing scripts, directories, missing JBang, and invalid working directories.
- Generate unambiguous names when filenames collide.

### Tests

- Running `a/Hello.java` cannot reuse `b/Hello.java`.
- An existing configuration for the same canonical path is reused.
- The same filename in two directories produces distinct configurations.
- A context-produced configuration is temporary.
- Invalid fields produce `RuntimeConfigurationError`.
- Both Run and Debug executors still find a `ProgramRunner`.
- The registered context/gutter action executes the selected file's configuration.

## PR 3 — Align script detection with JBang

First verify special filenames and directive placement against the current JBang CLI, then encode that behavior as executable tests.

### Changes

- Recognize directives only at column zero.
- Stop scanning after the initial directive/comment block as JBang does.
- Resolve the `jbang.java` versus `build.java` documentation/code conflict.
- Define and test the scan limit consistently.
- Ensure the file-based index uses exactly the same rules as direct detection.

### Tests

- Indented `//DEPS` does not create a root.
- `//DEPS` after Java code does not create a root.
- A shebang plus initial directives creates a root.
- Every officially special filename behaves like the JBang CLI.
- The direct detector and persisted index return identical results.
- Opening a falsely detected file does not show JBang actions, gutter icons, or status state.
- Real roots still expose actions and gutter markers.

## PR 4 — Improve directive editing

### Changes

- Support completion for every token in multi-value `//DEPS`, `//SOURCES`, and `//FILES` directives.
- Add completion for `//JAVA` versions, `//REPOS` shortcuts, and `//MAIN` classes.
- Correct diagnostic ranges with multiple spaces and multiple arguments.
- Add quick fixes to uppercase directives, remove duplicate dependencies, synchronize unresolved dependencies, and open JBang settings when unavailable.

### Tests

Use realistic fixture input and `myFixture.complete()`/`doHighlighting()`.

- Completion works after the second dependency or path.
- `target=source` completion preserves the mapping target.
- Java version and repository suggestions appear.
- Diagnostic ranges cover only the offending token.
- Applying each quick fix produces the expected document text or invokes the registered action.
- Tests fail if contributors, annotators, or quick fixes are not registered.

## PR 5 — Define language support honestly

### Changes

- Audit Java, Kotlin, Groovy, JShell, and `.jbang` behavior separately.
- Add full directive editing support for JShell where IntelliJ PSI permits it.
- Decide whether `.jbang` is intentionally directive-only.
- Document partial support instead of claiming parity where parity is impossible.
- Hide unsupported actions instead of showing controls that cannot work.

### Tests

For every supported language, test:

- Root recognition.
- Directive completion.
- Diagnostics.
- Path navigation.
- Run action visibility.
- Gutter visibility where applicable.
- Run and Debug runner discovery.

For partial languages, add tests asserting the intended limited behavior so it cannot drift accidentally.

## PR 6 — Replace the stale catalog schema

### Changes

- Prefer a schema maintained or generated by the main JBang repository.
- Update Java versions and current alias/template properties.
- Establish an explicit schema update process.
- Preserve local `script-ref` completion and navigation.

### Tests

- Parse and validate the bundled schema.
- Current Java versions are accepted.
- Template properties from a realistic catalog are accepted.
- Invalid catalog fields remain highlighted.
- `myFixture.complete()` offers local `script-ref` values.
- Ctrl/Cmd-click resolves local references.
- Record the upstream schema version/hash so accidental drift is visible.

## PR 7 — Fix lifecycle, cancellation, and notifications

### Changes

- Replace bare `CoroutineScope(Dispatchers.IO)` instances with project/service-scoped coroutines.
- Cancel work when the project closes or the user cancels.
- Prevent stale sync results from replacing newer results.
- Stop showing redundant success balloons while retaining status-widget feedback.
- Keep actionable failure notifications.
- Surface installation failures instead of swallowing exceptions.

### Tests

- Disposing a project cancels pending resolution.
- Cancelled resolution does not update libraries or status.
- An older sync completion cannot overwrite a newer sync.
- A successful explicit sync updates the widget without a balloon.
- A failed sync produces one actionable notification.
- Install failure reports an error and offers the download/settings fallback.
- Threading tests verify model changes occur with write access on EDT.

## PR 8 — IntelliJ UI polish and discoverability

### Changes

- Use native Run and Debug iconography.
- Replace the hand-styled hyperlink button with IntelliJ's link component.
- Make settings searchable.
- Add Documentation, Report Issue, and Show Logs links.
- Point feature tips to published documentation.
- Add accessible names, tooltips, and sensible mnemonics.

### Tests

- Action registration exposes the correct names and icons.
- Settings search terms include `JBang`, `sync`, `executable`, and `notifications`.
- Link components target expected URLs through an injected browser opener.
- Feature tips point to published docs, not GitHub source.
- UI component tests verify labels are associated with controls.
- Settings state round-trips through reset/apply.

## PR 9 — Make documentation accurate and useful

### Changes

Add:

- Troubleshooting.
- Known limitations.
- Architecture/integration model.
- Network and command execution behavior.
- Kotlin K2 module-library exception.
- Complete settings reference with defaults.
- Command preview and Copy button documentation.
- A real `CONTRIBUTING.md`.
- A `.DS_Store` ignore rule.

Update screenshots after UI changes stabilize.

### Tests

Extend `DocumentationTest` to verify:

- Every navigation entry resolves.
- Every image exists.
- Internal xrefs resolve.
- Troubleshooting covers executable lookup, sync failure, logs, WSL, and Kotlin.
- Known limitations references open issues.
- Documentation no longer claims IntelliJ modules are never modified.
- Command preview and Copy are documented.
- Feature-tip URLs target existing published pages.
- README links to `CONTRIBUTING.md`.
- `.gitignore` covers `.DS_Store`.

## PR 10 — Repair CI and compatibility checks

### Changes

- Either implement genuine UI tests or remove the misleading workflow.
- Use Java 25 and `./gradlew` consistently.
- Add Gradle wrapper validation.
- Actually run Qodana, or remove claims/configuration pretending that it runs.
- Replace deprecated GitHub `set-output` usage.
- Add a Linux smoke test using a real pinned JBang release to resolve dependencies, create a script, and run a script.
- Track Plugin Verifier deprecation counts and prevent increases.

### Tests

- Workflow contract tests parse YAML and verify Java/Gradle commands.
- The UI workflow invokes a dedicated UI test task, not ordinary `test`.
- A smoke fixture resolves and executes with the pinned JBang version.
- The plugin artifact builds before smoke installation.
- CI fails if verifier compatibility fails or deprecated API count increases.
- A release workflow test verifies stable and EAP channels.

## Recommended execution order

1. Safe script creation.
2. Run configuration identity and validation.
3. Script detection correctness.
4. Directive completion and quick fixes.
5. Language-support contract.
6. Catalog schema.
7. Lifecycle and cancellation.
8. IntelliJ UI polish.
9. Documentation consistency.
10. CI and API cleanup.

Documentation must be updated inside each PR as behavior changes. PR 9 is the final consistency and troubleshooting pass, not an excuse to defer changelog entries.
