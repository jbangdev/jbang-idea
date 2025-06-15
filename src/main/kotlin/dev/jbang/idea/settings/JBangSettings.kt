package dev.jbang.idea.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "JBangSettings",
    storages = [Storage("jbang.xml")]
)
class JBangSettings : PersistentStateComponent<JBangSettings> {
    var jbangExecutablePath: String? = null

    override fun getState(): JBangSettings {
        return this
    }

    override fun loadState(state: JBangSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): JBangSettings {
            return com.intellij.openapi.components.ServiceManager.getService(JBangSettings::class.java)
        }
    }
} 