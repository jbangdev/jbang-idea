package dev.jbang.idea.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.DefaultProgramRunner

/**
 * ProgramRunner that supports both Run and Debug executors for JBang configs.
 * Without this, IntelliJ doesn't show the Debug button for JBang run configurations.
 */
class JBangProgramRunner : DefaultProgramRunner() {

    override fun getRunnerId(): String = "JBangProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return profile is JBangRunConfiguration &&
            (executorId == DefaultRunExecutor.EXECUTOR_ID || executorId == DefaultDebugExecutor.EXECUTOR_ID)
    }
}
