///usr/bin/env jbang
//DEPS com.openai:openai-java:4.52.0

// OpenAIClient is compiled from Kotlin and carries kotlin.Metadata.
// It verifies K2 can expose Kotlin-library light classes to Java JBang scripts.
import com.openai.client.OpenAIClient;

class KotlinLibrary {
    public static void main(String[] args) {
        OpenAIClient client = null;
        System.out.println("Kotlin dependency resolves: " + client);
    }
}
