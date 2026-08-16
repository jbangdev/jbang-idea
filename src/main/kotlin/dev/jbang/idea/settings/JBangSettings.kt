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
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    // ponytail: direct field access via instance.state is cleaner, but these accessors
    // are used everywhere and changing all call sites is churn for no functional gain.
    var jbangPath: String by myState::jbangPath
    var autoSync: Boolean by myState::autoSync
    var askToOpenSelectedRoot: Boolean by myState::askToOpenSelectedRoot
    var openSelectedRootWithoutAsking: Boolean by myState::openSelectedRootWithoutAsking

    companion object {
        val instance: JBangSettings
            get() = ApplicationManager.getApplication().getService(JBangSettings::class.java)
    }
}
