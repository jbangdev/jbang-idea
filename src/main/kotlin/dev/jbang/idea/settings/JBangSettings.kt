package dev.jbang.idea.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

@State(name = "JBangSettings", storages = [Storage("jbang.xml")])
@Service(Service.Level.APP)
class JBangSettings : PersistentStateComponent<JBangSettings.State> {

    data class State(
        var jbangPath: String = "",
        var autoSync: Boolean = true,
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    var jbangPath: String
        get() = myState.jbangPath
        set(value) { myState.jbangPath = value }

    var autoSync: Boolean
        get() = myState.autoSync
        set(value) { myState.autoSync = value }

    companion object {
        val instance: JBangSettings
            get() = ApplicationManager.getApplication().getService(JBangSettings::class.java)
    }
}
