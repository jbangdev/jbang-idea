package dev.jbang.intellij.plugins.jbang.run

import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

class JbangRunConfigurationOptions : RunConfigurationOptions() {
    private val jbangScriptName: StoredProperty<String?> = string("").provideDelegate(this, "scriptName");
    private val jbangScriptOptions: StoredProperty<String?> = string("").provideDelegate(this, "scriptOptions");
    private val jbangScriptArgs: StoredProperty<String?> = string("").provideDelegate(this, "scriptArgs");

    fun getScriptName(): String? {
        return jbangScriptName.getValue(this)
    }

    fun setScriptName(scriptName: String?) {
        jbangScriptName.setValue(this, scriptName ?: "")
    }

    fun getScriptOptions(): String? {
        return jbangScriptOptions.getValue(this)
    }

    fun setScriptOptions(scriptOptions: String?) {
        jbangScriptOptions.setValue(this, scriptOptions ?: "")
    }

    fun getScriptArgs(): String? {
        return jbangScriptArgs.getValue(this)
    }

    fun setScriptArgs(scriptArgs: String?) {
        jbangScriptArgs.setValue(this, scriptArgs ?: "")
    }

}