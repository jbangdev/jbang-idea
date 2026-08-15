# Agent Instructions for jbang-newidea

## Test-First Development

**Every change starts with a failing test.** No exceptions.

### Bug fix workflow

1. Write a test that reproduces the bug (must fail)
2. Run the test, confirm it fails
3. Fix the code
4. Run the test, confirm it passes
5. Run all tests, confirm nothing broke

### Feature workflow

1. Write tests for the feature (must fail or not compile)
2. Run the tests, confirm they fail
3. Implement the feature
4. Run the tests, confirm they pass
5. Run all tests, confirm nothing broke

### What to test

**Always test the user-visible contract.** Internal/unit tests are fine too — they catch regressions faster and document intent. But every feature must have at least one test that verifies what the user actually sees: does the button appear, does the gutter icon show, does the completion offer results?

Ask: "if I only had internal tests and the registration/wiring was wrong, would any test fail?" If not, add one that would.

Checklist before saying "done":

- [ ] Is there a test that would fail if this feature/fix were reverted?
- [ ] Does the test verify the user-facing behavior, not just an internal method?
- [ ] For UI features (gutter icons, buttons, menu items): is there a test that checks registration/visibility via IntelliJ's APIs (`ProgramRunner.getRunner()`, `findAllGutters()`, etc.)?
- [ ] For run configurations: can both Run AND Debug executors find a runner?
- [ ] For completions/inspections: does the test use `myFixture.complete()`/`myFixture.doHighlighting()` on realistic input?

## IntelliJ Platform conventions

### Logging

Use `jbangLog<MyClass>()` and the lazy `log.debug { }` extension. Zero overhead when debug is off.

Enable at runtime: `Help → Diagnostic Tools → Debug Log Settings` → `#dev.jbang.idea`

### Threading

- `AdditionalLibraryRootsListener.fireAdditionalLibraryChanged()` needs write access on EDT
- `hasRunningCommands()` on terminal widgets must be called OFF EDT
- Use `DumbService.smartInvokeLater` or `invokeLater(ModalityState.nonModal())` for deferred work
- `TerminalToolWindowManager.createNewSession()` must be called on EDT

### Run configurations

- `getOptionsClass()` must be overridden — without it, `getOptions()` throws `ClassCastException`
- A `ProgramRunner` must be registered for each executor ID the config supports — no runner = no button
- Use `PtyCommandLine` for real terminal behavior (stdin, ANSI, signals)

### PSI and the shebang

- `///usr/bin/env jbang` is parsed as `PsiDocComment` (Javadoc), not `PsiComment`
- Always handle both `PsiComment` and `PsiDocComment` when walking jbang files
- The shebang causes Java parse errors in test fixtures — use `safeHighlight()` pattern or avoid shebang in highlighting tests

### Files outside source roots

- `RunLineMarkerContributor` does NOT run on files outside source roots
- Right-click actions (`EditorPopupMenu`) work everywhere — use `JBangRunScriptAction` as fallback
- Hide the right-click action when inside source roots to avoid duplication (`ProjectFileIndex.isInSourceContent()`)

## Build and test

```sh
./gradlew test              # run all tests
./gradlew test --tests "dev.jbang.idea.run.*"  # run specific tests
./gradlew runIde            # launch sandboxed IDE with plugin
./gradlew buildPlugin       # build distributable zip
```

Tail plugin logs during `runIde`:
```sh
tail -f .intellijPlatform/sandbox/jbang-idea-plugin/IU-2026.2/log/idea.log | grep jbang
```
