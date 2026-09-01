package dev.jbang.idea.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

@State(name = "JBangSettings", storages = [Storage("jbang.xml")])
@Service(Service.Level.APP)
class JBangSettings : PersistentStateComponent<JBangSettings.State> {

    data class State(
        var jbangPath: String = "",
        var autoSync: Boolean = true,
        var askToOpenSelectedRoot: Boolean = true,
        var openSelectedRootWithoutAsking: Boolean = false,
        var notifySyncErrors: Boolean = false,
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    // ponytail: direct field access via instance.state is cleaner, but these accessors
    // are used everywhere and changing all call sites is churn for no functional gain.
    var jbangPath: String
        get() = myState.jbangPath
        set(value) { myState.jbangPath = value }
    var autoSync: Boolean
        get() = myState.autoSync
        set(value) { myState.autoSync = value }
    var askToOpenSelectedRoot: Boolean
        get() = myState.askToOpenSelectedRoot
        set(value) { myState.askToOpenSelectedRoot = value }
    var openSelectedRootWithoutAsking: Boolean
        get() = myState.openSelectedRootWithoutAsking
        set(value) { myState.openSelectedRootWithoutAsking = value }
    var notifySyncErrors: Boolean
        get() = myState.notifySyncErrors
        set(value) { myState.notifySyncErrors = value }

    companion object {
        val instance: JBangSettings
            get() = ApplicationManager.getApplication().getService(JBangSettings::class.java)
    }
}
