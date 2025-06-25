package dev.jbang.idea.ui.toolbar

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiManager
import dev.jbang.idea.JBangCli.resolveScriptInfo
import dev.jbang.idea.ScriptInfo
import dev.jbang.idea.actions.SyncDependenciesAction
import dev.jbang.idea.isJBangScript
import dev.jbang.idea.isJBangScriptFile
import dev.jbang.idea.utils.ActionUtils

class SyncDepsAction : BaseToolbarAction() {
    private val log: Logger = Logger.getInstance(SyncDepsAction::class.java)
    override fun actionPerformed(e: AnActionEvent) {
        val jbangScriptFile = e.getData(CommonDataKeys.PSI_FILE)
        log.info("SyncDepsAction, $jbangScriptFile")
        if (jbangScriptFile != null && isJBangScriptFile(jbangScriptFile.name)) {
            if (isJBangScript(jbangScriptFile.text)) {
                log.info("SyncDepsAction, have valid JBang script file")
                val module = ModuleUtilCore.findModuleForPsiElement(jbangScriptFile)
                if (module != null) {
                    log.info("SyncDepsAction, using module: $module")
                    // Use the existing SyncDependenciesAction#syncDepsToModule to do the work
                    val action = ActionUtils.getActionByIdAs<SyncDependenciesAction>("jbang.SyncDependenciesAction")
                    log.info("SyncDepsAction, using action: $action")
                    action?.syncDepsToModule(module, jbangScriptFile)
                }
            }
        }
    }
}
