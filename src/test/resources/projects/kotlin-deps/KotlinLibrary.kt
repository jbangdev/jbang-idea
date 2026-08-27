#!/usr/bin/env jbang
//DEPS com.openai:openai-java:4.52.0

// The same Kotlin-compiled dependency, consumed from a Kotlin JBang script.
import com.openai.client.OpenAIClient

fun main() {
    val client: OpenAIClient? = null
    println("Kotlin dependency resolves: $client")
}
