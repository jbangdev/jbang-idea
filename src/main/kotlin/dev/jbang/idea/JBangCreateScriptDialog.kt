package dev.jbang.idea

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.ListTableModel
import dev.jbang.idea.cli.JBangCli
import dev.jbang.idea.cli.TemplateInfo
import dev.jbang.idea.cli.TemplateLookupResult
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.JPanel
import javax.swing.Timer
import javax.swing.event.DocumentEvent

/**
 * Single dialog for creating a JBang script: template (optional) + file name.
 * If no template is selected, `jbang init` uses the file extension to pick a default.
 * OK is disabled until a non-blank name is entered.
 */
internal fun interface TemplateResolver {
    fun resolve(reference: String, onResult: (TemplateLookupResult) -> Unit)
}

private fun backgroundTemplateResolver(project: Project) = TemplateResolver { reference, onResult ->
    object : Task.Backgroundable(project, "Loading JBang template properties", true) {
        private lateinit var result: TemplateLookupResult

        override fun run(indicator: ProgressIndicator) {
            result = JBangCli.lookupTemplate(reference)
        }

        override fun onSuccess() = onResult(result)
    }.queue()
}

internal data class TemplatePropertyRow(
    val key: String,
    val description: String,
    val defaultValue: String?,
    var value: String,
)

class JBangCreateScriptDialog internal constructor(
    project: Project,
    templates: List<TemplateInfo>,
    private val templateResolver: TemplateResolver,
) : DialogWrapper(project) {

    constructor(project: Project, templates: List<TemplateInfo>) :
        this(project, templates, backgroundTemplateResolver(project))

    internal val templateList = JBList(templates).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer { _, value, _, isSelected, _ ->
            javax.swing.JLabel(if (value.description.isNotBlank()) "${value.name} — ${value.description}" else value.name).apply {
                isOpaque = true
                border = javax.swing.BorderFactory.createEmptyBorder(2, 6, 2, 6)
                if (isSelected) {
                    background = javax.swing.UIManager.getColor("List.selectionBackground")
                    foreground = javax.swing.UIManager.getColor("List.selectionForeground")
                }
            }
        }
    }

    internal val templateField = JBTextField(30).apply {
        emptyText.text = "template or template@catalog"
    }
    internal val templateLookupButton = JButton("Load properties").apply {
        addActionListener { lookupTypedTemplate() }
    }
    internal val templateLookupStatus = JBLabel("Select a template or enter a catalog-qualified ID")
    private val templateInput = JPanel(BorderLayout(6, 4)).apply {
        val fieldAndButton = JPanel(BorderLayout(6, 0)).apply {
            add(templateField, BorderLayout.CENTER)
            add(templateLookupButton, BorderLayout.EAST)
        }
        add(fieldAndButton, BorderLayout.NORTH)
        add(templateLookupStatus, BorderLayout.SOUTH)
    }
    private val templateLookupTimer = Timer(600) { lookupTypedTemplate() }.apply { isRepeats = false }
    internal val nameField = JBTextField(30)

    private val propertyColumns: Array<ColumnInfo<TemplatePropertyRow, *>> = arrayOf(
        object : ColumnInfo<TemplatePropertyRow, String>("Property") {
            override fun valueOf(item: TemplatePropertyRow) = item.key
        },
        object : ColumnInfo<TemplatePropertyRow, String>("Value") {
            override fun valueOf(item: TemplatePropertyRow) = item.value
            override fun isCellEditable(item: TemplatePropertyRow) = true
            override fun setValue(item: TemplatePropertyRow, value: String?) {
                item.value = value.orEmpty()
            }
        },
        object : ColumnInfo<TemplatePropertyRow, String>("Description") {
            override fun valueOf(item: TemplatePropertyRow) = item.description
        },
    )
    private val propertyModel = ListTableModel<TemplatePropertyRow>(*propertyColumns)
    internal val propertyTable: TableView<TemplatePropertyRow> = TableView(propertyModel).apply {
        emptyText.text = "Select or resolve a template to load declared properties"
        setMinRowHeight(24)
    }
    internal val propertyScrollPane = JBScrollPane(propertyTable).apply {
        preferredSize = java.awt.Dimension(500, 130)
    }
    private val propertyComponent = LabeledComponent.create<JComponent>(
        propertyScrollPane,
        "Template properties:",
    )

    val selectedTemplate: String?
        get() = templateField.text.trim().ifBlank { null }
    val scriptName: String get() = nameField.text.trim()
    val propertyOverrides: Map<String, String>
        get() {
            propertyTable.stopEditing()
            return propertyTable.items
                .filter { it.value != it.defaultValue.orEmpty() }
                .associateTo(LinkedHashMap()) { it.key to it.value }
        }

    private var updatingTemplateSelection = false
    private var templateLookupGeneration = 0

    init {
        title = "New JBang Script"
        templateList.addListSelectionListener {
            if (!it.valueIsAdjusting && !updatingTemplateSelection) {
                val template = templateList.selectedValue
                updatingTemplateSelection = true
                templateField.text = template?.let(::templateReference).orEmpty()
                updatingTemplateSelection = false
                templateLookupTimer.stop()
                updateSelectedTemplate(template, templateField.text)
                if (template == null) {
                    propertyTable.emptyText.text = "Select or resolve a template to load declared properties"
                }
                templateLookupStatus.text = template?.let {
                    "Selected ${templateReference(it)} from loaded templates"
                } ?: "Select a template or enter a catalog-qualified ID"
            }
        }
        templateField.addActionListener { lookupTypedTemplate() }
        templateField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (updatingTemplateSelection) return
                templateLookupGeneration++
                templateLookupButton.isEnabled = true
                templateLookupStatus.toolTipText = null
                val reference = templateField.text.trim()
                val template = templates.find { reference == it.name || reference == templateReference(it) }
                updatingTemplateSelection = true
                if (template == null) templateList.clearSelection()
                else templateList.setSelectedValue(template, true)
                updatingTemplateSelection = false
                updateSelectedTemplate(template, reference)
                templateLookupTimer.stop()
                if (template != null) {
                    templateLookupStatus.text = "Selected ${templateReference(template)} from loaded templates"
                } else if ('@' in reference) {
                    propertyTable.emptyText.text = "Loading declared properties..."
                    val templateName = reference.substringBeforeLast('@')
                    val catalogName = reference.substringAfterLast('@')
                    templateLookupStatus.text = "Looking for $templateName in catalog $catalogName"
                    templateLookupTimer.restart()
                } else {
                    propertyTable.emptyText.text = "Select or resolve a template to load declared properties"
                    templateLookupStatus.text = if (reference.isBlank()) {
                        "Select a template or enter a catalog-qualified ID"
                    } else {
                        "Using custom template ID $reference; add @catalog to load declared properties"
                    }
                }
            }
        })
        nameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = updateOk()
        })
        init()
        updateOk()
    }

    private var lastSuggested = ""

    private fun templateReference(template: TemplateInfo): String =
        template.fullName.ifBlank { template.name }

    private fun updateSelectedTemplate(template: TemplateInfo?, reference: String) {
        val suggestionSource = template?.name ?: reference.substringBefore('@').ifBlank { null }
        val suggested = JBangCreateScriptAction.suggestFileName(suggestionSource)
        if (nameField.text.isBlank() || nameField.text == lastSuggested) {
            nameField.text = suggested
        }
        lastSuggested = suggested
        updateProperties(template)
    }

    private fun lookupTypedTemplate() {
        templateLookupTimer.stop()
        val reference = templateField.text.trim()
        if (reference.isBlank() || '@' !in reference) return
        templateLookupButton.isEnabled = false
        propertyTable.emptyText.text = "Loading declared properties..."
        val generation = ++templateLookupGeneration
        val templateName = reference.substringBeforeLast('@')
        val catalogName = reference.substringAfterLast('@')
        templateLookupStatus.text = "Looking for $templateName in catalog $catalogName"
        templateResolver.resolve(reference) { result ->
            if (generation != templateLookupGeneration || templateField.text.trim() != reference) return@resolve
            templateLookupButton.isEnabled = true
            templateLookupStatus.toolTipText = result.catalogRef
            val template = result.template
            updateProperties(template)
            if (template == null) {
                propertyTable.emptyText.text = "Properties unavailable because the template could not be resolved"
            }
            val resolvedCatalog = result.catalogName ?: catalogName
            templateLookupStatus.text = if (template != null) {
                "Resolved ${templateReference(template)} in catalog $resolvedCatalog"
            } else {
                "Could not resolve $reference in catalog $resolvedCatalog: ${result.error ?: "template not found"}"
            }
        }
    }

    private fun updateProperties(template: TemplateInfo?) {
        if (template != null) {
            propertyTable.emptyText.text = "This template has no declared properties"
        }
        val rows = template?.properties.orEmpty().map { (key, property) ->
            TemplatePropertyRow(
                key = key,
                description = property.description,
                defaultValue = property.defaultValue,
                value = property.defaultValue.orEmpty(),
            )
        }
        propertyModel.items = rows
    }

    private fun updateOk() {
        isOKActionEnabled = nameField.text.isNotBlank()
    }

    public override fun createCenterPanel(): JComponent {
        val scrollPane = JBScrollPane(templateList).apply {
            preferredSize = java.awt.Dimension(400, 200)
        }
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Available templates:", scrollPane)
            .addLabeledComponent("Template (optional):", templateInput)
            .addLabeledComponent("File name:", nameField)
            .addComponent(propertyComponent)
            .panel
    }

    public override fun dispose() {
        templateLookupTimer.stop()
        super.dispose()
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isBlank()) return ValidationInfo("File name is required", nameField)
        return null
    }

    override fun getPreferredFocusedComponent() = nameField
}
