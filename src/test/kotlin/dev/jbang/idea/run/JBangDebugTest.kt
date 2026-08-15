package dev.jbang.idea.run

import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test

class JBangDebugTest : LightJavaCodeInsightFixtureTestCase() {

    @Test
    fun testDebugCommandLineHasDebugFlag() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration
        config.scriptPath = "/tmp/tako.java"
        config.scriptArgs = "--name world"

        val cmd = JBangDebugRunState.buildDebugCommandLine(config, 4004)
        val cmdLine = cmd.commandLineString
        assertTrue("Should contain --debug", cmdLine.contains("--debug"))
        assertTrue("Should contain 4004", cmdLine.contains("4004"))
        assertTrue("Should contain run", cmdLine.contains("run"))
        assertTrue("Should contain script", cmdLine.contains("/tmp/tako.java"))
    }

    @Test
    fun testDebugShellCommandHasDebugFlag() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration
        config.scriptPath = "/tmp/tako.java"

        val cmd = JBangDebugRunState.buildDebugShellCommand(config, 4004)
        assertTrue("Should contain --debug", cmd.contains("--debug"))
        assertTrue("Should contain 4004", cmd.contains("4004"))
        assertTrue("Should contain run", cmd.contains("run"))
    }

    @Test
    fun testDebugStateIsReturnedForDebugExecutor() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration
        config.scriptPath = "/tmp/tako.java"

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environment = com.intellij.execution.runners.ExecutionEnvironmentBuilder
            .create(project, executor, config)
            .build()

        // Normal debug → JBangDebugRunState
        config.runInTerminal = false
        val debugState = config.getState(DefaultDebugExecutor.getDebugExecutorInstance(), environment)
        assertInstanceOf(debugState, JBangDebugRunState::class.java)

        // Terminal debug → JBangTerminalDebugRunState
        config.runInTerminal = true
        val debugTermState = config.getState(DefaultDebugExecutor.getDebugExecutorInstance(), environment)
        assertInstanceOf(debugTermState, JBangTerminalDebugRunState::class.java)
    }

    @Test
    fun testDebugPortDefault() {
        assertEquals(4004, JBangDebugRunState.DEFAULT_DEBUG_PORT)
    }

    @Test
    fun testProgramRunnerSupportsDebugExecutor() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration

        // This is the actual check IntelliJ uses to decide whether to show the Debug button
        val debugRunner = com.intellij.execution.runners.ProgramRunner.getRunner(
            DefaultDebugExecutor.EXECUTOR_ID, config
        )
        assertNotNull("A ProgramRunner must exist for Debug executor + JBangRunConfiguration", debugRunner)
    }

    @Test
    fun testProgramRunnerSupportsRunExecutor() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration

        val runRunner = com.intellij.execution.runners.ProgramRunner.getRunner(
            DefaultRunExecutor.EXECUTOR_ID, config
        )
        assertNotNull("A ProgramRunner must exist for Run executor + JBangRunConfiguration", runRunner)
    }
}
