package dev.jbang.idea

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DocumentationTest {
    private val docs = Path.of("docs/modules/ROOT/pages")

    @Test
    fun `documentation describes overlay workflows instead of removed integrations`() {
        val all = listOf("index.adoc", "installation.adoc", "usage.adoc")
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

        listOf("index.adoc", "installation.adoc", "usage.adoc").forEach { page ->
            Regex("image:([^\\[]+)").findAll(Files.readString(docs.resolve(page))).forEach { image ->
                val path = docs.parent.resolve("assets/images").resolve(image.groupValues[1])
                assertTrue("Missing documentation image: $path", Files.exists(path))
            }
        }
    }
}
