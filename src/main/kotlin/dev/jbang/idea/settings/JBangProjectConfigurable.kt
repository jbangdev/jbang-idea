package dev.jbang.idea.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * Project-level JBang settings: directive properties passed to jbang as `-Dname=value`
 * for editor resolution and Run/Debug. Shown under Settings → Tools → JBang → Properties.
 */
class JBangProjectConfigurable(private val project: Project) : Configurable {

    private val model = object : DefaultTableModel(arrayOf<Any>("Name", "Value"), 0) {
        override fun isCellEditable(row: Int, column: Int) = true
    }
    private val table = JBTable(model)
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Properties"

    override fun createComponent(): JComponent {
        val decorated = ToolbarDecorator.createDecorator(table)
            .setAddAction { model.addRow(arrayOf("", "")) }
            .setRemoveAction {
                if (table.isEditing) table.cellEditor?.stopCellEditing()
                val row = table.selectedRow
                if (row >= 0) model.removeRow(table.convertRowIndexToModel(row))
            }
            .createPanel()
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponentFillVertically(
                "Directive properties (passed to jbang as -Dname=value):",
                decorated,
            )
            .panel
        reset()
        return panel!!
    }

    private fun currentRows(): LinkedHashMap<String, String> {
        if (table.isEditing) table.cellEditor?.stopCellEditing()
        val rows = LinkedHashMap<String, String>()
        for (i in 0 until model.rowCount) {
            val name = (model.getValueAt(i, 0) as? String)?.trim().orEmpty()
            val value = (model.getValueAt(i, 1) as? String).orEmpty()
            if (name.isNotEmpty()) rows[name] = value
        }
        return rows
    }

    override fun isModified(): Boolean =
        currentRows() != LinkedHashMap(JBangProjectSettings.getInstance(project).properties)

    override fun apply() {
        JBangProjectSettings.getInstance(project).properties = currentRows()
    }

    override fun reset() {
        model.rowCount = 0
        JBangProjectSettings.getInstance(project).properties.forEach { (name, value) ->
            model.addRow(arrayOf(name, value))
        }
    }
}
