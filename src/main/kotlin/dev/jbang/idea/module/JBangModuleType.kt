package dev.jbang.idea.module

import com.intellij.openapi.module.ModuleType
import dev.jbang.idea.jbangIcon
import javax.swing.Icon

class JBangModuleType : ModuleType<JBangModuleBuilder>("JBang") {

    override fun createModuleBuilder(): JBangModuleBuilder {
        return JBangModuleBuilder()
    }

    override fun getName(): String {
        return "JBang"
    }

    override fun getDescription(): String {
        return "JBangModuleType"
    }

    override fun getNodeIcon(isOpened: Boolean): Icon {
        return jbangIcon
    }
}