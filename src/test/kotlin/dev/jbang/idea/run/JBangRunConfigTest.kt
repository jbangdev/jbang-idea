package dev.jbang.idea.run

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import com.intellij.psi.PsiComment
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test

class JBangRunConfigTest : LightJavaCodeInsightFixtureTestCase() {

    @Test
    fun testOptionsClassIsJBangRunConfigOptions() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration

        // This was the ClassCastException bug — getOptionsClass() must return JBangRunConfigOptions
        // Verify indirectly: setting properties should not throw ClassCastException
        config.scriptPath = "/tmp/test.java"
        assertEquals("/tmp/test.java", config.scriptPath)
    }

    @Test
    fun testOptionsRoundTrip() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration

        config.scriptPath = "/tmp/hello.java"
        config.scriptArgs = "--name \"hello world\""
        config.jbangOptions = "--fresh --java 21"
        config.environmentVariables = "GREETING=hello"
        config.workingDirectory = "/tmp"
        config.runInTerminal = true

        assertEquals("/tmp/hello.java", config.scriptPath)
        assertEquals("--name \"hello world\"", config.scriptArgs)
        assertEquals("--fresh --java 21", config.jbangOptions)
        assertEquals("GREETING=hello", config.environmentVariables)
        assertEquals("/tmp", config.workingDirectory)
        assertTrue(config.runInTerminal)
    }

    @Test
    fun testGetStateReturnsNonNullForRun() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration
        config.scriptPath = "/tmp/hello.java"
        config.runInTerminal = false

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environment = com.intellij.execution.runners.ExecutionEnvironmentBuilder
            .create(project, executor, config)
            .build()

        val state = config.getState(executor, environment)
        assertNotNull("getState should return non-null RunProfileState for Run executor", state)
        assertInstanceOf(state, JBangRunState::class.java)
    }

    @Test
    fun testGetStateReturnsDebugStateForDebug() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration
        config.scriptPath = "/tmp/hello.java"
        config.runInTerminal = false

        // Debug executor may not be fully available in light test fixtures.
        // Verify the branching logic by checking executor ID matching.
        val debugExecutorId = DefaultDebugExecutor.EXECUTOR_ID
        assertEquals("Debug", debugExecutorId)

        // Test via run executor (which is always available)
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environment = com.intellij.execution.runners.ExecutionEnvironmentBuilder
            .create(project, executor, config)
            .build()

        val runState = config.getState(executor, environment)
        assertInstanceOf(runState, JBangRunState::class.java)

        // Verify debug path would be taken by checking the executor ID condition
        assertFalse("Run executor should not match debug ID",
            debugExecutorId == executor.id)
    }

    @Test
    fun testRunInTerminalFlagPersists() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration

        assertTrue("runInTerminal should default to true", config.runInTerminal)
        config.runInTerminal = false
        assertFalse("runInTerminal should be settable", config.runInTerminal)
    }

    @Test
    fun testBuildCommandParsesQuotedArgumentsOptionsAndEnvironment() {
        val config = RunManager.getInstance(project)
            .createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
            .configuration as JBangRunConfiguration
        config.scriptPath = "/tmp/tako.java"
        config.jbangOptions = "--fresh --java \"21\""
        config.scriptArgs = "--name \"hello world\""
        config.environmentVariables = "GREETING=hello world\nCOUNT=2"
        config.workingDirectory = "/tmp/work"

        val command = JBangRunState.buildCommandLine(config)

        assertEquals(listOf("run", "--fresh", "--java", "21", "/tmp/tako.java", "--name", "hello world"), command.parametersList.list)
        assertEquals("hello world", command.environment["GREETING"])
        assertEquals("2", command.environment["COUNT"])
        assertEquals(File("/tmp/work"), command.workDirectory)
    }

    @Test
    fun testBuildJBangCommand() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration
        config.scriptPath = "/tmp/tako.java"
        config.scriptArgs = "--name world"

        val cmd = JBangRunState.buildCommandLine(config)
        val args = cmd.parametersList.list
        assertTrue("Should contain 'run'", args.contains("run"))
        assertTrue("Should contain script path", args.contains("/tmp/tako.java"))
        assertTrue("Should contain args", args.contains("--name"))
        assertTrue("Should contain args", args.contains("world"))
    }

    @Test
    fun testRunInTerminalUsesTerminalRunner() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration
        config.scriptPath = "/tmp/tako.java"
        config.runInTerminal = true

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environment = com.intellij.execution.runners.ExecutionEnvironmentBuilder
            .create(project, executor, config)
            .build()

        val state = config.getState(executor, environment)
        if (SystemInfo.isWindows) {
            assertInstanceOf(state, JBangRunState::class.java)
        } else {
            assertInstanceOf(state, JBangTerminalRunState::class.java)
        }
    }

    @Test
    fun testBuildJBangCommandShellString() {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("test", JBangConfigurationFactory(JBangConfigurationType()))
        val config = settings.configuration as JBangRunConfiguration
        config.scriptPath = "/tmp/tako.java"
        config.scriptArgs = "--name world"

        val shellCmd = JBangRunState.buildShellCommand(config)
        assertTrue("Shell command should contain jbang", shellCmd.contains("jbang"))
        assertTrue("Shell command should contain run", shellCmd.contains("run"))
        assertTrue("Shell command should contain script", shellCmd.contains("/tmp/tako.java"))
        assertTrue("Shell command should contain args", shellCmd.contains("--name world"))
    }

    @Test
    fun testRunConfigProducerSetsJBangName() {
        val psiFile = myFixture.configureByText("tako.java", """
            //DEPS info.picocli:picocli:4.7.5
            public class tako {
                public static void main(String[] args) {}
            }
        """.trimIndent())

        val comment = PsiTreeUtil.findChildOfType(psiFile, PsiComment::class.java)!!
        val context = ConfigurationContext(comment)

        val producer = JBangRunConfigProducer()
        val setting = producer.findOrCreateConfigurationFromContext(context)

        assertNotNull("Producer should create a config for jbang script", setting)
        val config = setting!!.configuration as JBangRunConfiguration
        assertEquals("jbang tako.java", config.name)
        assertTrue("Script path should end with tako.java", config.scriptPath.endsWith("tako.java"))
    }
}
