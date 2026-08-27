package dev.jbang.idea

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class SampleProjectsTest {
    private val samples = Path.of("src/test/resources/projects")

    @Test
    fun `manual sample projects include core scenarios`() {
        val expected = listOf(
            "single-root/Hello.java",
            "single-root/src/Message.java",
            "single-root/config/app.properties",
            "multi-root/RootA.java",
            "multi-root/RootB.java",
            "multi-root/src/AHelper.java",
            "multi-root/src/BHelper.java",
            "catalog/jbang-catalog.json",
            "catalog/scripts/hello.java",
            "errors/Broken.java",
            "kotlin-deps/KotlinLibrary.java",
            "kotlin-deps/KotlinLibrary.kt",
            "README.md", 
        )

        expected.forEach { assertTrue("Missing sample: $it", Files.isRegularFile(samples.resolve(it))) }
    }

    @Test
    fun `runnable samples use complete dependency coordinates`() {
        listOf(
            "single-root/Hello.java", "multi-root/RootA.java", "multi-root/RootB.java",
            "kotlin-deps/KotlinLibrary.java", "kotlin-deps/KotlinLibrary.kt",
        ).forEach { sample ->
            Files.readAllLines(samples.resolve(sample))
                .filter { it.startsWith("//DEPS ") }
                .forEach { directive ->
                    val parts = directive.removePrefix("//DEPS ").split(':')
                    assertTrue("Incomplete dependency in $sample: $directive", parts.size >= 3 && parts.take(3).all(String::isNotBlank))
                }
        }
    }

    @Test
    fun `sample directives point to existing local files`() {
        val hello = Files.readString(samples.resolve("single-root/Hello.java"))
        assertTrue(hello.contains("//SOURCES src/Message.java"))
        assertTrue(hello.contains("//FILES config/app.properties"))
        assertTrue(Files.exists(samples.resolve("single-root/src/Message.java")))
        assertTrue(Files.exists(samples.resolve("single-root/config/app.properties")))
    }
}
