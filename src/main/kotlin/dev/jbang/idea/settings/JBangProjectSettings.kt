package dev.jbang.idea.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Project-scoped JBang settings, stored in `.idea/jbang.xml` so they can be shared.
 *
 * Currently holds user-defined directive properties (`${name}` / `${name:default}`
 * substitutions). They are passed to `jbang` as `-Dname=value` for editor resolution
 * and Run/Debug, so a project can pin the values its scripts expect.
 */
@State(name = "JBangProjectSettings", storages = [Storage("jbang.xml")])
@Service(Service.Level.PROJECT)
class JBangProjectSettings : PersistentStateComponent<JBangProjectSettings.State> {

    data class State(
        // LinkedHashMap keeps the user's row order stable in the UI and on disk.
        var properties: MutableMap<String, String> = LinkedHashMap(),
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    var properties: Map<String, String>
        get() = myState.properties
        set(value) { myState.properties = LinkedHashMap(value) }

    /** Properties as `-Dname=value` arguments, skipping blank names. */
    fun asDFlags(): List<String> =
        myState.properties.entries
            .filter { it.key.isNotBlank() }
            .map { "-D${it.key}=${it.value}" }

    companion object {
        fun getInstance(project: Project): JBangProjectSettings =
            project.getService(JBangProjectSettings::class.java)
    }
}
