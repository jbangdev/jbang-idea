package dev.jbang.idea

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class DocumentationTest {
    private val docs = Path.of("docs/modules/ROOT/pages")

    @Test
    fun `successful main builds publish an EAP version`() {
        val workflow = Files.readString(Path.of(".github/workflows/build.yml"))

        assertTrue("Missing EAP publication job", workflow.contains("publishEarlyAccess:"))
        assertTrue("EAP versions must be unique", workflow.contains("-eap.\${{ github.run_number }}"))
        assertTrue("EAP publication needs the Marketplace token", workflow.contains("PUBLISH_TOKEN: \${{ secrets.PUBLISH_TOKEN }}"))
        assertTrue("Stable releases must also reach EAP subscribers", Files.readString(Path.of(".github/workflows/release.yml")).contains("-PpluginChannel=eap"))
        assertTrue("Gradle must support an explicit channel", Files.readString(Path.of("build.gradle.kts")).contains("pluginChannel"))
    }

    @Test
    fun `CI identifies and bounds hanging tests`() {
        val workflow = Files.readString(Path.of(".github/workflows/build.yml"))
        val build = Files.readString(Path.of("build.gradle.kts"))

        assertTrue("CI test step needs a timeout", workflow.contains("timeout-minutes: 15"))
        assertTrue("CI needs per-test progress", build.contains("testLogging") && build.contains("TestLogEvent.STARTED"))
    }

    @Test
    fun `marketplace title is JBang`() {
        val properties = Properties().apply {
            Files.newInputStream(Path.of("gradle.properties")).use(::load)
        }
        assertTrue("Marketplace title should be JBang", properties.getProperty("pluginName") == "JBang")
    }

    @Test
    fun `release draft uses notes for the configured plugin version`() {
        val workflow = Files.readString(Path.of(".github/workflows/build.yml"))
        assertFalse("Release draft ignores the versioned changelog", workflow.contains("getChangelog --unreleased"))
    }

    @Test
    fun `documentation and contributor guidance cover support workflows`() {
        val pages = listOf(
            "index.adoc", "features.adoc", "installation.adoc", "usage.adoc",
            "architecture.adoc", "troubleshooting.adoc", "known-limitations.adoc",
        )
        val all = pages
            .joinToString("\n") { Files.readString(docs.resolve(it)) }

        listOf(
            "JBang project wizard",
            "Sync Dependencies between JBang and Gradle",
            "Sync Dependencies to IDEA's module",
        ).forEach { assertFalse("Removed feature is still documented: $it", all.contains(it)) }

        val usage = Files.readString(docs.resolve("usage.adoc"))
        listOf(
            "== Synchronization",
            "== Multiple JBang roots",
            "== Sources and files",
            "== Completion and navigation",
            "== Run and Debug",
            "== Java versions and JDKs",
            "== Creating scripts",
        ).forEach { assertTrue("Missing usage section: $it", usage.contains(it)) }

        val features = Files.readString(docs.resolve("features.adoc"))
        listOf(
            "== Project integration and synchronization",
            "== Multiple JBang roots",
            "== Completion and diagnostics",
            "== Sources and navigation",
            "== Run and Debug",
            "== Java versions and dependencies",
            "== Creating scripts",
        ).forEach { assertTrue("Missing feature section: $it", features.contains(it)) }
        assertTrue("Feature overview should be screenshot-rich", Regex("image:").findAll(features).count() >= 7)
        assertTrue("Script creation command preview must be documented", all.contains("Copy") && all.contains("command preview"))
        assertTrue("Troubleshooting must explain logs, WSL and Kotlin", listOf("idea.log", "WSL", "Kotlin").all(all::contains))
        assertTrue("Known limitations must reference the wildcard-import issue", all.contains("issues/163"))
        assertFalse("Standalone Kotlin support is a documented module-library exception", all.contains("IntelliJ modules are never modified"))
        assertTrue("README should direct humans to contributor documentation", Files.readString(Path.of("README.md")).contains("CONTRIBUTING.md"))
        assertTrue("macOS metadata must be ignored", Files.readString(Path.of(".gitignore")).contains(".DS_Store"))

        pages.forEach { page ->
            Regex("image:([^\\[]+)").findAll(Files.readString(docs.resolve(page))).forEach { image ->
                val path = docs.parent.resolve("assets/images").resolve(image.groupValues[1])
                assertTrue("Missing documentation image: $path", Files.exists(path))
            }
        }
    }

    @Test
    fun `feature tips link to published documentation`() {
        val source = Files.readString(Path.of("src/main/kotlin/dev/jbang/idea/JBangFeatureTips.kt"))
        assertTrue("Feature tips must point to published docs", source.contains("jbang.dev/documentation/jbang-idea"))
        assertFalse("Feature tips must not point to GitHub AsciiDoc source", source.contains("github.com/jbangdev/jbang-idea/blob"))
    }

    @Test
    fun `CI declarations match what actually runs`() {
        val build = Files.readString(Path.of(".github/workflows/build.yml"))
        assertTrue("CI must validate the Gradle wrapper", build.contains("wrapper-validation"))
        assertFalse("GitHub set-output is deprecated", build.contains("::set-output"))
        assertFalse("A fake UI-test workflow is worse than no UI-test workflow", Files.exists(Path.of(".github/workflows/run-ui-tests.yml")))
        assertFalse("Unused Qodana configuration should not imply Qodana runs", Files.exists(Path.of("qodana.yml")))
    }
}
