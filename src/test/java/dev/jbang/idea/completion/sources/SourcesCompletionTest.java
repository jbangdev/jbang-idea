package dev.jbang.idea.completion.sources;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.Test;

import java.util.List;

public class SourcesCompletionTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/testdata";
    }

    @Test
    public void testCompletionInJava() {
        myFixture.configureByFiles("completion/sources/hello.java", "completion/sources/UserService.java");
        myFixture.complete(CompletionType.BASIC);
        List<String> lookupElementStrings = myFixture.getLookupElementStrings();
        assertNotNull(lookupElementStrings);
        assertSameElements(lookupElementStrings, "UserService.java");
    }

}
