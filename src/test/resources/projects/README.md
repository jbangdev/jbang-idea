# JBang plugin sample projects

Open any directory below as a project in the sandbox IDE started by `./gradlew runIde`.

- `single-root/` — dependency overlay, `//SOURCES`, `//FILES`, completion, navigation, Run/Debug.
- `multi-root/` — two roots with isolated dependencies; use the JBang status widget to switch context.
- `catalog/` — Cmd/Ctrl-click the local `script-ref` in `jbang-catalog.json`.
- `errors/` — intentional unknown directive, invalid and duplicate GAVs, and missing `//SOURCES`/`//FILES` resources.
- `kotlin-deps/` — Java and Kotlin scripts consuming OpenAI's Kotlin-compiled `OpenAIClient`; open either script and confirm the import resolves.

Useful checks:

- Press Ctrl+Space after `//DEPS`, `//SOURCES`, or `//FILES`.
- Cmd/Ctrl-click local paths.
- Confirm dependency types resolve in roots and included sources.
- Run `RootA.java`, `RootB.java`, `Hello.java`, either `kotlin-deps` script, or the catalog script.

The `errors/` sample is intentionally not runnable.
