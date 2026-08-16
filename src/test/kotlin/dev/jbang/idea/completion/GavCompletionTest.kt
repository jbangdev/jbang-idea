package dev.jbang.idea.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class GavCompletionTest : LightJavaCodeInsightFixtureTestCase() {

    fun testCompletesArtifactsFromRemoteMavenRepository() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/search") { exchange ->
            val query = URLDecoder.decode(exchange.requestURI.rawQuery, StandardCharsets.UTF_8)
            if (query.contains("a:*") || query.contains("v:*")) {
                exchange.sendResponseHeaders(400, -1)
                exchange.close()
            } else {
                val response = """{"response":{"docs":[{"g":"org.remote","a":"artifact","latestVersion":"1.2.3"}]}}"""
                exchange.sendResponseHeaders(200, response.length.toLong())
                exchange.responseBody.use { it.write(response.toByteArray()) }
            }
        }
        server.start()
        val previous = System.getProperty("jbang.maven.search.url")
        System.setProperty("jbang.maven.search.url", "http://localhost:${server.address.port}/search")
        try {
            myFixture.configureByText("Remote.java", "//DEPS org.remote:<caret>\nclass Remote {}")

            myFixture.complete(CompletionType.BASIC)

            val lookup = myFixture.lookupElements.orEmpty().single { it.lookupString == "org.remote:artifact:" }
            val presentation = LookupElementPresentation()
            lookup.renderElement(presentation)
            assertEquals(" remote", presentation.tailText)
        } finally {
            if (previous == null) System.clearProperty("jbang.maven.search.url")
            else System.setProperty("jbang.maven.search.url", previous)
            server.stop(0)
        }
    }

    fun testShowsArtifactAndSnapshotStatusForLocalVersion() {
        withLocalArtifact("org/apache/commons/commons-lang3/3.18.0-SNAPSHOT") {
            myFixture.configureByText(
                "Hello.java",
                "//DEPS org.apache.commons:commons-lang3:<caret>\nclass Hello {}",
            )

            myFixture.complete(CompletionType.BASIC)
            val lookup = myFixture.lookupElements.orEmpty().single { it.lookupString == "3.18.0-SNAPSHOT" }
            val presentation = LookupElementPresentation()
            lookup.renderElement(presentation)

            assertEquals("org.apache.commons:commons-lang3", presentation.typeText)
            assertEquals(" local snapshot", presentation.tailText)
        }
    }

    fun testCompletesArtifactsFromRealLocalMavenRepository() {
        withLocalArtifact("org/apache/commons/commons-lang3/3.17.0") {
            myFixture.configureByText("Hello.java", "//DEPS org.apache.commons:<caret>\nclass Hello {}")

            myFixture.complete(CompletionType.BASIC)

            val lookup = myFixture.lookupElements.orEmpty().single {
                it.lookupString == "org.apache.commons:commons-lang3:"
            }
            val presentation = LookupElementPresentation()
            lookup.renderElement(presentation)

            assertEquals(" local", presentation.tailText)
        }
    }

    private fun withLocalArtifact(path: String, test: () -> Unit) {
        val repository = Files.createTempDirectory("jbang-m2")
        val artifact = repository.resolve(path)
        Files.createDirectories(artifact)
        Files.writeString(artifact.resolve("artifact.pom"), "<project/>")
        val previousRepo = System.getProperty("maven.repo.local")
        val previousUrl = System.getProperty("jbang.maven.repo.url")
        val previousSearch = System.getProperty("jbang.maven.search.url")
        System.setProperty("maven.repo.local", repository.toString())
        // Prevent tests from hitting real Maven Central
        System.setProperty("jbang.maven.repo.url", "http://localhost:0")
        System.setProperty("jbang.maven.search.url", "http://localhost:0")
        try {
            test()
        } finally {
            if (previousRepo == null) System.clearProperty("maven.repo.local") else System.setProperty("maven.repo.local", previousRepo)
            if (previousUrl == null) System.clearProperty("jbang.maven.repo.url") else System.setProperty("jbang.maven.repo.url", previousUrl)
            if (previousSearch == null) System.clearProperty("jbang.maven.search.url") else System.setProperty("jbang.maven.search.url", previousSearch)
            FileUtil.delete(repository)
        }
    }
}
