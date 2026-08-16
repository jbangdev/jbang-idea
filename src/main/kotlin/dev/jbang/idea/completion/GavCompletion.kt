package dev.jbang.idea.completion

import com.google.gson.JsonParser
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContextImpl
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.util.ProcessingContext
import dev.jbang.idea.JBangPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * GAV completion for `//DEPS group:artifact:version`.
 * Single class — registered once per language via plugin.xml.
 *
 * - No colon: full-text search (e.g. `google.guava`)
 * - One colon: artifact search by groupId (e.g. `com.google.guava:`)
 * - Two colons: version search (e.g. `com.google.guava:guava:`)
 */
@Suppress("UnstableApiUsage")
class GavCompletion : CompletionContributor(), DumbAware {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(PsiComment::class.java),
            GavCompletionProvider
        )
    }
}

@Suppress("UnstableApiUsage")
private object GavCompletionProvider : CompletionProvider<CompletionParameters>() {

    private val MAVEN_ICON = JBangPlugin.icon16 // ponytail: reuse jbang icon, add maven icon later if needed
    private val HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val comment = parameters.position as? PsiComment ?: return
        val text = comment.text
        if (!text.startsWith("//DEPS ")) return

        val rawSearch = text.substring(7).trim()
        val searchText = trimDummy(rawSearch)
        if (searchText.isBlank()) return

        val startOffset = comment.textRange.startOffset + 7
        val parts = searchText.split(':')
        val isVersionSearch = parts.size == 3

        val searchService = service<DependencyCompletionService>()
        val searchContext = DependencyCompletionContextImpl(comment.project, ProjectSystemId("GRADLE"))

        var priority = 10000.0

        fun addLookup(lookupString: String, source: DependencyCompletionContributionSource? = null) {
            var lookup = LookupElementBuilder.create(lookupString).withIcon(MAVEN_ICON)
            if (isVersionSearch) lookup = lookup.withTypeText(parts.take(2).joinToString(":"), true)
            val tail = buildString {
                if (source != null) append(if (source == DependencyCompletionContributionSource.LOCAL) " local" else " remote")
                if (lookupString.endsWith("SNAPSHOT", ignoreCase = true)) append(" snapshot")
            }
            if (tail.isNotEmpty()) lookup = lookup.withTailText(tail, true)
            result.addElement(
                PrioritizedLookupElement.withPriority(lookup.withInsertHandler { insertionContext, selectedItem ->
                        var selectedText = selectedItem.lookupString
                        if (isVersionSearch) {
                            val artifactInfo = searchText.substring(0, searchText.lastIndexOf(':'))
                            selectedText = "$artifactInfo:$selectedText"
                        }
                        val editor = insertionContext.editor
                        val document = insertionContext.document
                        val currentOffset = editor.caretModel.offset
                        document.deleteString(startOffset, currentOffset)
                        document.insertString(startOffset, selectedText)
                        editor.caretModel.moveToOffset(startOffset + selectedText.length)
                    }, priority--)
            )
        }

        runBlockingCancellable {
            withContext(Dispatchers.IO) { localMavenCompletions(searchText) }
                .forEach { addLookup(it, DependencyCompletionContributionSource.LOCAL) }

            val remote = mavenCentralCompletions(searchText)
            remote.forEach { addLookup(it, DependencyCompletionContributionSource.SERVER) }
            if (remote.isNotEmpty()) return@runBlockingCancellable

            when (parts.size) {
                2 -> {
                    val request = DependencyArtifactCompletionRequest(parts[0], parts[1], searchContext)
                    searchService.suggestArtifactCompletions(request).collect { event ->
                        if (event is DependencyCompletionEvent.Item) addLookup(
                            "${parts[0]}:${event.result.result}:",
                            event.result.source,
                        )
                    }
                }
                3 -> {
                    val request = DependencyVersionCompletionRequest(parts[0], parts[1], parts[2], searchContext)
                    searchService.suggestVersionCompletions(request).collect { event ->
                        if (event is DependencyCompletionEvent.Item) addLookup(
                            event.result.result,
                            event.result.source,
                        )
                    }
                }
                else -> {
                    val request = DependencyCompletionRequest(searchText, searchContext)
                    searchService.suggestCompletions(request).collect { event ->
                        if (event is DependencyCompletionEvent.Item) {
                            val item = event.result
                            addLookup(
                                "${item.groupId}:${item.artifactId}:${item.version}",
                                item.source,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun trimDummy(value: String): String {
        return StringUtil.trim(
            value.replace(CompletionUtil.DUMMY_IDENTIFIER, "")
                .replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, "")
        )
    }

    private suspend fun mavenCentralCompletions(searchText: String): List<String> {
        val parts = searchText.split(':')
        // For version completion, use maven-metadata.xml (always current)
        if (parts.size == 3) return mavenMetadataVersions(parts[0], parts[1], parts[2])

        val query = when (parts.size) {
            1 -> parts[0]
            2 -> "g:\"${parts[0]}\"" + parts[1].takeIf(String::isNotEmpty)?.let { " AND a:$it*" }.orEmpty()
            else -> return emptyList()
        }
        val endpoint = System.getProperty("jbang.maven.search.url")
            ?: "https://search.maven.org/solrsearch/select"
        val uri = URI.create("$endpoint?wt=json&rows=50&q=${URLEncoder.encode(query, StandardCharsets.UTF_8)}")

        return try {
            val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build()
            val response = HTTP.sendCancellable(request)
            if (response.statusCode() !in 200..299) return emptyList()
            val docs = JsonParser.parseString(response.body()).asJsonObject
                .getAsJsonObject("response").getAsJsonArray("docs")
            docs.mapNotNull { value ->
                val doc = value.asJsonObject
                val group = doc.get("g")?.asString ?: return@mapNotNull null
                val artifact = doc.get("a")?.asString ?: return@mapNotNull null
                "$group:$artifact:${if (parts.size == 1) doc.get("latestVersion")?.asString.orEmpty() else ""}"
            }.distinct().take(50)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Fetches versions from repo1.maven.org/maven2 maven-metadata.xml — always up to date. */
    private suspend fun mavenMetadataVersions(group: String, artifact: String, prefix: String): List<String> {
        val path = group.replace('.', '/')
        val baseUrl = System.getProperty("jbang.maven.repo.url") ?: "https://repo1.maven.org/maven2"
        val uri = URI.create("$baseUrl/$path/$artifact/maven-metadata.xml")
        return try {
            val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build()
            val response = HTTP.sendCancellable(request)
            if (response.statusCode() !in 200..299) return emptyList()
            val root = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(java.io.ByteArrayInputStream(response.body().toByteArray()))
            val nodes = root.getElementsByTagName("version")
            val versions = (0 until nodes.length).map { nodes.item(it).textContent }
                .filter { prefix.isEmpty() || it.startsWith(prefix) }
            // Return newest first (metadata lists oldest first)
            versions.asReversed().take(50)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun HttpClient.sendCancellable(request: HttpRequest): HttpResponse<String> =
        suspendCancellableCoroutine { continuation ->
            val future = sendAsync(request, HttpResponse.BodyHandlers.ofString())
            continuation.invokeOnCancellation { future.cancel(true) }
            future.whenComplete { response, error ->
                if (!continuation.isActive) return@whenComplete
                if (error != null) continuation.resumeWithException(error) else continuation.resume(response)
            }
        }

    private fun localMavenCompletions(searchText: String): List<String> {
        val parts = searchText.split(':')
        val repository = Path.of(
            System.getProperty("maven.repo.local")
                ?: Path.of(System.getProperty("user.home"), ".m2", "repository").toString()
        )
        val directory = when (parts.size) {
            2 -> repository.resolve(parts[0].replace('.', '/'))
            3 -> repository.resolve(parts[0].replace('.', '/')).resolve(parts[1])
            else -> return emptyList()
        }
        if (!Files.isDirectory(directory)) return emptyList()

        return Files.list(directory).use { entries ->
            entries.filter(Files::isDirectory)
                .map { it.fileName.toString() }
                .filter { it.startsWith(parts.last()) }
                .sorted()
                .limit(50)
                .map { if (parts.size == 2) "${parts[0]}:$it:" else it }
                .toList()
        }
    }
}
