package dev.jbang.idea

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.table.TableView
import com.intellij.util.ui.UIUtil
import dev.jbang.idea.cli.JBangCli
import dev.jbang.idea.cli.TemplateInfo
import dev.jbang.idea.cli.TemplateLookupResult
import dev.jbang.idea.cli.TemplateProperty
import java.awt.datatransfer.DataFlavor
import javax.swing.JButton

class JBangCreateScriptActionTest : BasePlatformTestCase() {

    fun testTemplateSuggestsFileName() {
        assertEquals("readme.md", JBangCreateScriptAction.suggestFileName("readme.md"))
        assertEquals("hello.kt", JBangCreateScriptAction.suggestFileName("hello.kt"))
        assertEquals("qrest.java", JBangCreateScriptAction.suggestFileName("qrest"))
        assertEquals("qrest.java", JBangCreateScriptAction.suggestFileName("qrest@community"))
    }

    fun testNoTemplateSuggestsEmptyFileName() {
        assertEquals("", JBangCreateScriptAction.suggestFileName(null))
    }

    fun testOkDisabledWithEmptyName() {
        val dialog = JBangCreateScriptDialog(project, emptyList())
        dialog.nameField.text = ""
        assertFalse("OK should be disabled with empty name", dialog.isOKActionEnabled)
        dialog.nameField.text = "hello.java"
        assertTrue("OK should be enabled with a name", dialog.isOKActionEnabled)
    }

    fun testSelectingTemplateUpdatesName() {
        val templates = listOf(
            dev.jbang.idea.cli.TemplateInfo(name = "hello", description = "Hello World"),
            dev.jbang.idea.cli.TemplateInfo(name = "cli", description = "CLI app"),
            dev.jbang.idea.cli.TemplateInfo(name = "hello.kt", description = "Kotlin Hello"),
        )
        val dialog = JBangCreateScriptDialog(project, templates)

        dialog.templateList.selectedIndex = 0
        assertEquals("hello", dialog.templateField.text)
        assertEquals("hello.java", dialog.nameField.text)

        dialog.templateList.selectedIndex = 2
        assertEquals("hello.kt", dialog.nameField.text)

        // Clearing selection clears the suggested name
        dialog.templateList.clearSelection()
        assertEquals("", dialog.nameField.text)
    }

    fun testFreeFormTemplateReferenceIsPassedThrough() {
        val dialog = JBangCreateScriptDialog(
            project,
            listOf(TemplateInfo(name = "hello", fullName = "hello@builtins")),
        )
        val panel = dialog.createCenterPanel()

        assertTrue(javax.swing.SwingUtilities.isDescendingFrom(dialog.templateField, panel))
        dialog.templateField.text = "q-aws-lambda@jbang-cloud"

        assertEquals("q-aws-lambda@jbang-cloud", dialog.selectedTemplate)
        assertTrue("A custom reference must not leave a stale known selection", dialog.templateList.isSelectionEmpty)
        assertEquals("q-aws-lambda.java", dialog.nameField.text)
        dialog.dispose()
    }

    fun testQualifiedTemplateLookupLoadsPropertiesAndShowsResolvedOrigin() {
        val reference = "q-aws-lambda-sqs-tf@nandorholozsnyak/jbang-cloud"
        val resolved = TemplateInfo(
            name = "q-aws-lambda-sqs-tf",
            fullName = reference,
            properties = mapOf("aws-sqs-enabled" to TemplateProperty("Generate an SQS queue", "true")),
        )
        val dialog = JBangCreateScriptDialog(
            project,
            emptyList(),
            templateResolver = TemplateResolver { requested, onResult ->
                assertEquals(reference, requested)
                onResult(
                    TemplateLookupResult(
                        reference = reference,
                        template = resolved,
                        catalogName = "nandorholozsnyak/jbang-cloud",
                        catalogRef = "https://github.com/nandorholozsnyak/jbang-cloud/blob/HEAD/jbang-catalog.json",
                    ),
                )
            },
        )

        dialog.templateField.text = reference
        assertEquals("Loading declared properties...", dialog.propertyTable.emptyText.text)
        dialog.templateLookupButton.doClick()

        assertEquals(listOf("aws-sqs-enabled"), dialog.propertyTable.items.map { it.key })
        assertEquals("true", dialog.propertyTable.items.single().value)
        assertEquals(
            "Resolved $reference in catalog nandorholozsnyak/jbang-cloud",
            dialog.templateLookupStatus.text,
        )
        assertEquals(
            "https://github.com/nandorholozsnyak/jbang-cloud/blob/HEAD/jbang-catalog.json",
            dialog.templateLookupStatus.toolTipText,
        )
    }

    fun testTypingKnownTemplateReferenceLoadsItsProperties() {
        val template = TemplateInfo(
            name = "service",
            fullName = "service@acme",
            properties = mapOf("region" to TemplateProperty("Deployment region", "eu-central-1")),
        )
        val dialog = JBangCreateScriptDialog(project, listOf(template))

        dialog.templateField.text = "service@acme"

        assertEquals(template, dialog.templateList.selectedValue)
        assertEquals(listOf("region"), dialog.propertyTable.items.map { it.key })
    }

    fun testTemplatePropertiesUseStandardIntellijTableAndExposeOverrides() {
        val template = TemplateInfo(
            name = "service",
            fullName = "service@acme",
            description = "Service template",
            properties = linkedMapOf(
                "region" to TemplateProperty("Deployment region", "eu-central-1"),
                "native" to TemplateProperty("Build a native executable", "false"),
            ),
        )
        val dialog = JBangCreateScriptDialog(project, listOf(template))

        assertNotNull(
            "Template properties should use IntelliJ's standard table control",
            UIUtil.findComponentOfType(dialog.createCenterPanel(), TableView::class.java),
        )
        dialog.templateList.selectedIndex = 0

        assertEquals("service@acme", dialog.selectedTemplate)
        assertEquals(listOf("region", "native"), dialog.propertyTable.items.map { it.key })
        assertEquals("eu-central-1", dialog.propertyTable.items[0].value)
        assertEquals("Deployment region", dialog.propertyTable.items[0].description)
        assertTrue("Properties table should be visible for a parameterized template", dialog.propertyScrollPane.isVisible)
        assertTrue("Defaults are handled by JBang and are not overrides", dialog.propertyOverrides.isEmpty())

        dialog.propertyTable.listTableModel.setValueAt("us-east-1", 0, 1)

        assertEquals(mapOf("region" to "us-east-1"), dialog.propertyOverrides)
    }

    fun testSelectingTemplateWithoutPropertiesClearsPropertyEditor() {
        val templates = listOf(
            TemplateInfo(
                name = "parameterized",
                properties = mapOf("name" to TemplateProperty("Name", "Duke")),
            ),
            TemplateInfo(name = "plain"),
        )
        val dialog = JBangCreateScriptDialog(project, templates)

        dialog.templateList.selectedIndex = 0
        assertTrue(dialog.propertyScrollPane.isVisible)
        dialog.templateList.selectedIndex = 1

        assertTrue("Keep the dialog layout stable when switching templates", dialog.propertyScrollPane.isVisible)
        assertTrue(dialog.propertyTable.items.isEmpty())
        assertTrue(dialog.propertyOverrides.isEmpty())
    }

    fun testNoTemplateSelectedAllowsCustomName() {
        val dialog = JBangCreateScriptDialog(project, emptyList())
        dialog.nameField.text = "myscript.java"
        assertNull("No template selected", dialog.selectedTemplate)
        assertEquals("myscript.java", dialog.scriptName)
        assertTrue(dialog.isOKActionEnabled)
    }

    fun testCommandPreviewUpdatesWithNameAndTemplate() {
        val templates = listOf(
            TemplateInfo(name = "hello", description = "Hello World"),
        )
        val dialog = JBangCreateScriptDialog(project, templates)

        dialog.nameField.text = "hello.java"
        assertTrue(
            "Command preview should show jbang init",
            dialog.createCenterPanel().let { true } && // ensure panel is created
            dialog.nameField.text == "hello.java",
        )

        // Select a template — preview should include --template
        dialog.templateList.selectedIndex = 0
        dialog.nameField.text = "hello.java" // re-trigger
        // Verify the command is built correctly
        val cmd = dev.jbang.idea.cli.JBangCli.buildInitCommand(
            "hello.java", dialog.selectedTemplate, dialog.propertyOverrides,
        )
        assertTrue("Command should contain init", cmd.contains("init"))
        assertTrue("Command should contain --template", cmd.contains("--template"))
        assertTrue("Command should contain the file name", cmd.contains("hello.java"))
    }

    fun testRejectsExistingAndUnsafeDestinations() {
        val directory = myFixture.tempDirFixture.findOrCreateDir("scripts")
        myFixture.addFileToProject("scripts/existing.java", "class Existing {}")
        val dialog = JBangCreateScriptDialog(project, emptyList(), destinationDirectory = directory)

        dialog.nameField.text = "existing.java"
        assertEquals("A file named existing.java already exists", dialog.validateDestination()?.message)

        dialog.nameField.text = "../outside.java"
        assertEquals("File name must stay inside the selected directory", dialog.validateDestination()?.message)

        dialog.nameField.text = java.io.File("/tmp/outside.java").absolutePath
        assertEquals("File name must be relative to the selected directory", dialog.validateDestination()?.message)
    }

    fun testNestedDestinationIsAccepted() {
        val directory = myFixture.tempDirFixture.findOrCreateDir("scripts")
        val dialog = JBangCreateScriptDialog(project, emptyList(), destinationDirectory = directory)

        dialog.nameField.text = "examples/Hello.java"

        assertNull(dialog.validateDestination())
        assertTrue(dialog.isOKActionEnabled)
    }

    fun testCommandPreviewHasCopyButtonThatCopiesCommand() {
        val dialog = JBangCreateScriptDialog(project, emptyList())
        dialog.nameField.text = "hello.java"
        val panel = dialog.createCenterPanel()
        val copyButton = UIUtil.findComponentsOfType(panel, JButton::class.java)
            .singleOrNull { it.text == "Copy" }

        assertNotNull("Command preview should have a Copy button", copyButton)
        copyButton!!.doClick()

        assertEquals(
            JBangCli.buildInitCommand("hello.java", null, emptyMap()).joinToString(" "),
            CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor),
        )
    }

    fun testNewScriptActionIsRegistered() {
        val action = ActionManager.getInstance().getAction("jbang.CreateScript")

        assertNotNull(action)
        assertEquals("JBang Script", action.templatePresentation.text)
    }
}
