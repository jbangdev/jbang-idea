# How to Tag & Release

## Prerequisites

- Push access to `jbangdev/jbang-idea`
- `PUBLISH_TOKEN` secret configured (JetBrains Marketplace token)

## Steps

1. **Update version and changelog** in your PR:
   - Set `pluginVersion` in `gradle.properties` (e.g. `0.26.0`)
   - Add entries under `## [Unreleased]` in `CHANGELOG.md`

2. **Merge the PR** to `main`.

3. **Create a Git tag** on `main`:
   ```bash
   git tag v0.26.0
   git push origin v0.26.0
   ```

4. **Create a GitHub Release**:
   ```bash
   gh release create v0.26.0 --title "v0.26.0" --notes "Release notes here"
   ```
   Or use the GitHub UI: Releases → Draft a new release → select the tag → **Publish release**.

   > ⚠️ The release must be **published** (not draft). The workflow triggers on `released` or `prereleased` events only.

5. **The `release.yml` workflow** runs automatically and:
   - Builds the plugin
   - Publishes to JetBrains Marketplace via `./gradlew publishPlugin`
   - Uploads the artifact to the GitHub Release
   - Creates a changelog update PR

## Early access channel

Every successful build of `main` publishes a uniquely versioned build to the `eap` Marketplace channel.
To receive these builds, add this custom plugin repository in IntelliJ IDEA under
**Settings → Plugins → ⚙ → Manage Plugin Repositories**:

```text
https://plugins.jetbrains.com/plugins/eap/18257
```

Stable releases are also uploaded to `eap` because custom channels take precedence over the default channel.

## Common Pitfalls

- **Draft releases don't trigger the workflow** — you must publish them.
- **Java version** — `release.yml` must match `build.yml` (currently Java 25).
- **Tag must match a commit on `main`** — the workflow checks out by tag name.
