package dev.jbang.idea.completion.directive;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.Test;

import java.util.List;

public class BasicDirectiveCompletionTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/testdata";
    }

    @Test
    public void testCompletionInJava() {
        var psifile = myFixture.configureByFile("completion/basicdirective.java");

        myFixture.complete(CompletionType.BASIC);
        List<String> lookupElementStrings = myFixture.getLookupElementStrings();
        assertNotNull(lookupElementStrings);
        assertSameElements(lookupElementStrings, "CDS ", "DEPS ", "DESCRIPTION ","FILES ", "GAV ", "JAVA ", "JAVA_OPTIONS ", "JAVAAGENT ", "JAVAC_OPTIONS ", "REPOS ", "SOURCES ");
    }

   // @Test disabled unttil can figure out why kotlin and groovy are not activated intests
   /* public void testCompletionInKotlin() {
        var psifile = myFixture.configureByFile("completion/basicdirective.kt");

        myFixture.complete(CompletionType.BASIC);
        List<String> lookupElementStrings = myFixture.getLookupElementStrings();
        assertNotNull(lookupElementStrings);
        assertSameElements(lookupElementStrings, "KOTLIN ");
    }*/

 //   @Test
  /*  public void testCompletionInGroovy() {
        var psifile = myFixture.configureByFile("completion/basicdirective.groovy");

        myFixture.complete(CompletionType.BASIC);
        List<String> lookupElementStrings = myFixture.getLookupElementStrings();
        assertNotNull(lookupElementStrings);
        assertSameElements(lookupElementStrings, "KOTLIN ");

        assertContainsElements(lookupElementStrings,"DESCRIPTION", "GROOVY");
    }*/
}
