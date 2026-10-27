package dev.jbang.idea.project

import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.pom.Navigatable
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.jbang.idea.cli.ScriptInfo
import dev.jbang.idea.settings.JBangSettings
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class JBangLibraryProviderTest : LightJavaCodeInsightFixtureTestCase() {

    private var previousAutoSync = true

    override fun setUp() {
        super.setUp()
        previousAutoSync = JBangSettings.instance.autoSync
        JBangSettings.instance.autoSync = false
        JBangProjectService.getInstance(project).clear()
    }

    override fun tearDown() {
        JBangProjectService.getInstance(project).clear()
        JBangSettings.instance.autoSync = previousAutoSync
        super.tearDown()
    }

    private fun installInfo(path: String, info: ScriptInfo) {
        val roots = info.classpathJars.mapNotNull {
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(java.io.File(it))
                ?.let(JarFileSystem.getInstance()::getJarRootForLocalFile)
        }
        JBangProjectService.getInstance(project).cacheResolved(path, info, roots)
    }

    @Test
    fun testProviderTriggersClasspathFeatureTip() {
        val jar = Files.createTempFile("jbang-overlay", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val script = myFixture.addFileToProject("overlay.java", "//DEPS example:overlay:1\nclass overlay {}")
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))
        var tips = 0

        JBangLibraryProvider(showFeatureTip = { tips++ }).getAdditionalProjectLibraries(project)

        assertEquals(1, tips)
    }

    @Test
    fun testProviderDoesNotLoadUncachedClasspathPathsOnEdt() {
        val jar = Files.createDirectories(Files.createTempDirectory("jbang-overlay").resolve("deep/maven/path"))
            .resolve("overlay.jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val script = myFixture.addFileToProject("overlay.java", "//DEPS example:overlay:1\nclass overlay {}")
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))

        JBangLibraryProvider().getAdditionalProjectLibraries(project)
    }

    @Test
    fun testProviderExposesJavaSyntheticLibraryForPackageIndex() {
        // IDEA-393077: only a JavaSyntheticLibrary contributes its roots to PackageIndex
        // (fileSetsByPackagePrefix). A plain SyntheticLibrary gets DummyWorkspaceFileSetData,
        // so findPackage() returns null and Java wildcard/on-demand imports stay unresolved
        // while explicit imports/FQNs work. Guard the base type so the regression can't return.
        val jar = Files.createTempFile("jbang-overlay", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val script = myFixture.addFileToProject("overlay.java", "//DEPS example:overlay:1\nclass overlay {}")
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))

        val library = JBangLibraryProvider().getAdditionalProjectLibraries(project).single()

        assertTrue(
            "Synthetic library must be a JavaSyntheticLibrary so its roots enter PackageIndex " +
                "and Java wildcard imports resolve (IDEA-393077)",
            library is com.intellij.openapi.roots.JavaSyntheticLibrary,
        )
    }

    @Test
    fun testClasspathJarsAreExposedAsIndexableArchiveRoots() {
        val jar = Files.createTempFile("jbang-overlay", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val script = myFixture.addFileToProject("overlay.java", "//DEPS example:overlay:1\nclass overlay {}")

        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))

        val root = JBangLibraryProvider().getAdditionalProjectLibraries(project)
            .single().binaryRoots.single()

        assertSame("IntelliJ can only index the archive root, not the .jar file", JarFileSystem.getInstance(), root.fileSystem)
        assertTrue("The binary root should be the directory inside the archive", root.isDirectory)
    }

    @Test
    fun testOnlyKotlinLibrariesAreMirroredToActiveStandaloneModule() {
        val kotlinJar = Files.createTempFile("jbang-kotlin", ".jar").toFile()
        JarOutputStream(kotlinJar.outputStream()).use { it.putNextEntry(JarEntry("META-INF/test.kotlin_module")); it.closeEntry() }
        val javaJar = Files.createTempFile("jbang-java", ".jar").toFile()
        JarOutputStream(javaJar.outputStream()).use { }
        val root = myFixture.addFileToProject("KotlinDep.java", "//DEPS example:kotlin:1\nclass KotlinDep {}")
        installInfo(root.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(kotlinJar.path, javaJar.path)))
        JBangProjectService.getInstance(project).setActiveRoot(root.virtualFile.path)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(module, com.intellij.openapi.module.ModuleUtilCore.findModuleForFile(root.virtualFile, project))
        assertNull(com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId())
        val kotlinRoot = JBangProjectService.getInstance(project).getLibraryRoots(root.virtualFile.path)
            .single { it.name == kotlinJar.name }
        assertNotNull(kotlinRoot.findFileByRelativePath("META-INF/test.kotlin_module"))

        com.intellij.openapi.application.runWriteAction { JBangKotlinLibraryMirror.update(project, root.virtualFile) }

        val rootModel = com.intellij.openapi.roots.ModuleRootManager.getInstance(module).modifiableModel
        val library = rootModel.moduleLibraryTable.getLibraryByName("JBang Kotlin support — KotlinDep.java")
        assertNotNull("Kotlin dependencies must be visible to K2 through a script-specific module library", library)
        val roots = library!!.getUrls(com.intellij.openapi.roots.OrderRootType.CLASSES).toList()
        rootModel.dispose()
        assertEquals(listOf(kotlinRoot.url), roots)
        assertFalse("Plain Java dependencies stay per-script synthetic roots", roots.contains("jar://${javaJar.path}!/"))

        val otherRoot = myFixture.addFileToProject("nested/OtherKotlinDep.java", "//DEPS example:kotlin:1\nclass OtherKotlinDep {}")
        installInfo(otherRoot.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(kotlinJar.path)))
        JBangProjectService.getInstance(project).setActiveRoot(otherRoot.virtualFile.path)
        com.intellij.openapi.application.runWriteAction { JBangKotlinLibraryMirror.update(project, otherRoot.virtualFile) }

        val afterKotlinSwitch = com.intellij.openapi.roots.ModuleRootManager.getInstance(module).modifiableModel
        assertNull("The previous script name must not remain visible", afterKotlinSwitch.moduleLibraryTable.getLibraryByName("JBang Kotlin support — KotlinDep.java"))
        assertNotNull("The library name must identify the relevant script", afterKotlinSwitch.moduleLibraryTable.getLibraryByName("JBang Kotlin support — nested/OtherKotlinDep.java"))
        afterKotlinSwitch.dispose()

        val javaOnly = myFixture.addFileToProject("JavaOnly.java", "//DEPS example:java:1\nclass JavaOnly {}")
        installInfo(javaOnly.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(javaJar.path)))
        JBangProjectService.getInstance(project).setActiveRoot(javaOnly.virtualFile.path)
        com.intellij.openapi.application.runWriteAction { JBangKotlinLibraryMirror.update(project, javaOnly.virtualFile) }

        val afterSwitch = com.intellij.openapi.roots.ModuleRootManager.getInstance(module).modifiableModel
        assertNull("The Kotlin module library must be removed after leaving its root", afterSwitch.moduleLibraryTable.getLibraryByName("JBang Kotlin support — nested/OtherKotlinDep.java"))
        afterSwitch.dispose()
    }

    @Test
    fun testJbangFileResolveScopeIncludesDependencyClasses() {
        val jar = Files.createTempFile("jbang-overlay", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use {
            it.putNextEntry(JarEntry("example/OverlayType.class"))
            it.write(byteArrayOf(0))
            it.closeEntry()
        }
        val script = myFixture.configureByText(
            "overlay.java",
            "//DEPS example:overlay:1\nclass overlay { example.OverlayType value; }"
        )
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))
        fireLibraryChange(project)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val dependencyClass = JBangLibraryProvider().getAdditionalProjectLibraries(project)
            .single().binaryRoots.single().findFileByRelativePath("example/OverlayType.class")!!

        assertTrue(
            "A jbang script's Java resolve scope must include its dependency classes",
            JBangResolveScopeEnlarger().getAdditionalResolveScope(script.virtualFile, project)!!.contains(dependencyClass)
        )
    }

    /** Compile a single .java source and return the directory containing the .class files. */
    private fun compileToDir(source: String, className: String): File {
        val dir = Files.createTempDirectory("jbang-compile").toFile()
        val srcFile = File(dir, "$className.java")
        srcFile.writeText(source)
        val javac = ProcessBuilder("javac", "-d", dir.path, srcFile.path)
            .redirectErrorStream(true).start()
        assertEquals("javac failed: ${javac.inputStream.bufferedReader().readText()}", 0, javac.waitFor())
        return dir
    }

    /** Create a JAR with a compiled class file at the proper package path. */
    private fun jarWithClass(packageDir: String, className: String, source: String): File {
        val compileDir = compileToDir(source, className)
        val jar = Files.createTempFile("jbang-dep", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { jos ->
            val classFile = File(compileDir, "$packageDir/$className.class")
            assertTrue("Compiled class must exist: ${classFile.path}", classFile.exists())
            jos.putNextEntry(JarEntry("$packageDir/$className.class"))
            jos.write(classFile.readBytes())
            jos.closeEntry()
        }
        return jar
    }

    /**
     * Compile [apiSource] and [clientSource] together, then split the classes into two JARs,
     * like a declared dependency (client) and its transitive dependency (api).
     * [kotlinClient] marks the client JAR as a Kotlin library, as `openai-java-core` or
     * `spring-ai-client-chat` are.
     */
    private fun apiAndClientJars(apiSource: String, clientSource: String, kotlinClient: Boolean = false): Pair<File, File> {
        val dir = Files.createTempDirectory("jbang-compile").toFile()
        File(dir, "api").mkdirs()
        File(dir, "client").mkdirs()
        val api = File(dir, "api/Memory.java").apply { writeText(apiSource) }
        val client = File(dir, "client/Advisor.java").apply { writeText(clientSource) }
        val javac = ProcessBuilder("javac", "-d", dir.path, api.path, client.path)
            .redirectErrorStream(true).start()
        assertEquals("javac failed: ${javac.inputStream.bufferedReader().readText()}", 0, javac.waitFor())
        fun jar(entry: String, kotlin: Boolean = false) = Files.createTempFile("jbang-dep", ".jar").toFile().also { jar ->
            JarOutputStream(jar.outputStream()).use { jos ->
                if (kotlin) { jos.putNextEntry(JarEntry("META-INF/client.kotlin_module")); jos.closeEntry() }
                jos.putNextEntry(JarEntry(entry))
                jos.write(File(dir, entry).readBytes())
                jos.closeEntry()
            }
        }
        return jar("api/Memory.class") to jar("client/Advisor.class", kotlinClient)
    }

    private fun assertNoErrorsAcrossJarBoundary(kotlinClient: Boolean) {
        val (apiJar, clientJar) = apiAndClientJars(
            "package api; public interface Memory { static Memory create() { return null; } void clear(); }",
            "package client; public class Advisor { " +
                "public static Advisor builder(api.Memory memory) { return null; } " +
                "public static api.Memory memory() { return null; } }",
            kotlinClient,
        )
        val script = myFixture.addFileToProject(
            "Reproducer.java",
            """
            //DEPS example:client:1
            import api.Memory;
            import client.Advisor;

            class Reproducer {
                void run() {
                    Memory memory = Memory.create();
                    Advisor advisor = Advisor.builder(memory);
                    Memory returned = Advisor.memory();
                    var inferred = Advisor.memory();
                    inferred.clear();
                    Advisor chained = Advisor.builder(Advisor.memory());
                }
            }
            """.trimIndent(),
        )
        val service = JBangProjectService.getInstance(project)
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(clientJar.path, apiJar.path)))
        try {
            service.setActiveRoot(script.virtualFile.path)
            myFixture.configureFromExistingVirtualFile(script.virtualFile)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            // Light fixtures live on temp://, so the queued mirror update cannot find the root: mirror explicitly.
            com.intellij.openapi.application.runWriteAction { JBangKotlinLibraryMirror.update(project, script.virtualFile) }
            IndexingTestUtil.waitUntilIndexesAreReady(project)
            val rootModel = com.intellij.openapi.roots.ModuleRootManager.getInstance(module).modifiableModel
            val mirrored = rootModel.moduleLibraryTable.libraries.any { it.name?.startsWith("JBang Kotlin support") == true }
            rootModel.dispose()
            assertEquals("Only Kotlin dependencies are mirrored to the module", kotlinClient, mirrored)

            val errors = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.ERROR)
            assertEmpty(
                "Types declared in a transitive JAR must be compatible with the same types in a dependency's signatures",
                errors.map { it.description },
            )
        } finally {
            service.clear()
            com.intellij.openapi.application.runWriteAction { JBangKotlinLibraryMirror.update(project) }
        }
    }

    @Test
    fun testTypeFromTransitiveJarIsCompatibleAcrossJarBoundary() = assertNoErrorsAcrossJarBoundary(kotlinClient = false)

    /** Regression for #180: the declared dependency is a Kotlin library, its transitive one is not. */
    @Test
    fun testTypeFromTransitiveJarIsCompatibleWhenDependencyIsKotlinLibrary() = assertNoErrorsAcrossJarBoundary(kotlinClient = true)

    /** Create an empty JAR on disk and return its in-VFS jar root (what resolve scopes operate on). */
    private fun emptyJarRoot(name: String): Pair<File, VirtualFile> {
        val jar = Files.createTempFile(name, ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar)
            ?.let(JarFileSystem.getInstance()::getJarRootForLocalFile)
        assertNotNull("JAR root must be resolvable for $name", root)
        return jar to root!!
    }

    /**
     * Regression for the multi-root ownership invariant of #180 (Copilot review finding):
     * when a dependency JAR is shared by two root scripts with different classpaths, a class
     * inside that JAR must resolve against the *active* root's classpath only, never leaking the
     * other root's sibling JARs. A JAR owned by a single root always uses that root's classpath.
     */
    @Test
    fun testSharedJarResolvesAgainstActiveRootClasspathOnly() {
        val (aOnlyFile, aOnlyRoot) = emptyJarRoot("jbang-a-only")
        val (bOnlyFile, bOnlyRoot) = emptyJarRoot("jbang-b-only")
        val (sharedFile, sharedRoot) = emptyJarRoot("jbang-shared")

        val scriptA = myFixture.addFileToProject("RootA.java", "//DEPS a:only:1\nclass RootA {}")
        val scriptB = myFixture.addFileToProject("RootB.java", "//DEPS b:only:1\nclass RootB {}")
        val service = JBangProjectService.getInstance(project)
        installInfo(scriptA.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(aOnlyFile.path, sharedFile.path)))
        installInfo(scriptB.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(bOnlyFile.path, sharedFile.path)))

        // A JAR owned by a single root always resolves against that root, regardless of active root.
        service.setActiveRoot(scriptB.virtualFile.path)
        assertTrue(
            "A-only JAR must see root A's classpath even when B is active",
            service.getLibraryRootsForLibraryRoot(aOnlyRoot).containsAll(listOf(aOnlyRoot, sharedRoot)),
        )

        // The shared JAR must follow the active root: A active -> A's siblings, not B's.
        service.setActiveRoot(scriptA.virtualFile.path)
        val whenAActive = service.getLibraryRootsForLibraryRoot(sharedRoot)
        assertTrue("Shared JAR must see root A's sibling when A is active", aOnlyRoot in whenAActive)
        assertFalse("Shared JAR must not leak root B's sibling when A is active", bOnlyRoot in whenAActive)

        // Switching the active root to B flips the isolation the other way.
        service.setActiveRoot(scriptB.virtualFile.path)
        val whenBActive = service.getLibraryRootsForLibraryRoot(sharedRoot)
        assertTrue("Shared JAR must see root B's sibling when B is active", bOnlyRoot in whenBActive)
        assertFalse("Shared JAR must not leak root A's sibling when B is active", aOnlyRoot in whenBActive)
    }

    @Test
    fun testJbangDependencyClassResolvesInJavaScript() {
        val jar = jarWithClass("example", "Greeter",
            "package example; public class Greeter { public static String greet() { return \"hi\"; } }")

        val script = myFixture.addFileToProject(
            "hello.java",
            "//DEPS example:greeter:1\nimport example.Greeter;\nclass hello { Greeter g; }",
        )
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))
        fireLibraryChange(project)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        myFixture.configureFromExistingVirtualFile(script.virtualFile)
        val ref = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            script, com.intellij.psi.PsiJavaCodeReferenceElement::class.java,
        ).find { it.text == "Greeter" }
        assertNotNull("Should find reference to Greeter", ref)
        assertNotNull(
            "Dependency class from JAR must resolve in jbang script",
            ref!!.resolve(),
        )
    }

    @Test
    fun testJbangResolveScopeEnlargerWorksForKotlinFiles() {
        val jar = jarWithClass("example", "Greeter",
            "package example; public class Greeter { public static String greet() { return \"hi\"; } }")

        val script = myFixture.addFileToProject(
            "hello.kt",
            "//DEPS example:greeter:1\nfun main() { example.Greeter.greet() }",
        )
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))
        fireLibraryChange(project)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val scope = JBangResolveScopeEnlarger().getAdditionalResolveScope(script.virtualFile, project)
        assertNotNull("Kotlin jbang file must get an enlarged resolve scope", scope)

        val depClass = JBangLibraryProvider().getAdditionalProjectLibraries(project)
            .single().binaryRoots.single().findFileByRelativePath("example/Greeter.class")!!
        assertTrue(
            "Kotlin file's resolve scope must include dependency classes",
            scope!!.contains(depClass),
        )
    }

    @Test
    fun testStatusWidgetShowsSyncProgressAndFailure() {
        val root = myFixture.addFileToProject("RootA.java", "//DEPS example:a:1\nclass RootA {}")
        installInfo(root.virtualFile.path, ScriptInfo())
        val service = JBangProjectService.getInstance(project)
        service.setActiveRoot(root.virtualFile.path)
        val widget = JBangStatusBarWidgetFactory().createWidget(project) as JBangStatusWidget

        service.syncStarted(root.virtualFile.path)
        assertEquals("jbang: syncing RootA.java…", widget.getSelectedValue())

        service.syncFinished(
            root.virtualFile.path,
            succeeded = false,
            errors = listOf("missing source wonka", "invalid dependency example:broken"),
        )
        assertEquals("jbang: RootA.java (sync failed: 2 errors)", widget.getSelectedValue())
        assertTrue(widget.getTooltipText().contains("missing source wonka"))
        assertTrue(widget.getTooltipText().contains("invalid dependency example:broken"))

        service.syncStarted(root.virtualFile.path)
        service.syncFinished(root.virtualFile.path, succeeded = true)
        assertEquals("jbang: RootA.java (synced)", widget.getSelectedValue())
        assertNotNull(widget.getPopup())
        widget.dispose()
    }

    @Test
    fun testSingleRootWidgetOffersSyncPopup() {
        val root = myFixture.addFileToProject("RootA.java", "//DEPS example:a:1\nclass RootA {}")
        installInfo(root.virtualFile.path, ScriptInfo())
        JBangProjectService.getInstance(project).setActiveRoot(root.virtualFile.path)
        val widget = JBangStatusBarWidgetFactory().createWidget(project) as JBangStatusWidget

        assertNotNull(widget.getPopup())
        widget.dispose()
    }

    @Test
    fun testStatusWidgetShowsActiveRootAndOffersRootPicker() {
        val rootA = myFixture.addFileToProject("RootA.java", "//DEPS example:a:1\nclass RootA {}")
        val rootB = myFixture.addFileToProject("RootB.java", "//DEPS example:b:1\nclass RootB {}")
        installInfo(rootA.virtualFile.path, ScriptInfo())
        installInfo(rootB.virtualFile.path, ScriptInfo())
        val service = JBangProjectService.getInstance(project)
        service.setActiveRoot(rootA.virtualFile.path)
        val widget = JBangStatusBarWidgetFactory().createWidget(project) as JBangStatusWidget

        assertEquals("jbang: RootA.java", widget.getSelectedValue())
        service.setActiveRoot(rootB.virtualFile.path)
        assertEquals("jbang: RootB.java", widget.getSelectedValue())
        assertNotNull("Multiple roots should provide a picker", widget.getPopup())
        widget.dispose()
    }

    @Test
    fun testMultipleRootsKeepSeparateDependencyScopesAndWidgetChoices() {
        val jarA = Files.createTempFile("root-a", ".jar").toFile()
        val jarB = Files.createTempFile("root-b", ".jar").toFile()
        JarOutputStream(jarA.outputStream()).use { }
        JarOutputStream(jarB.outputStream()).use { }
        val rootA = myFixture.addFileToProject("RootA.java", "//DEPS example:a:1\nclass RootA {}")
        val rootB = myFixture.addFileToProject("RootB.java", "//DEPS example:b:1\nclass RootB {}")
        installInfo(rootA.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jarA.path)))
        installInfo(rootB.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jarB.path)))
        val service = JBangProjectService.getInstance(project)

        assertEquals(setOf(rootA.virtualFile.path, rootB.virtualFile.path), service.allRoots.keys)
        assertEquals(service.getLibraryRoots(rootA.virtualFile.path), service.getLibraryRootsForFile(rootA.virtualFile.path))
        assertEquals(service.getLibraryRoots(rootB.virtualFile.path), service.getLibraryRootsForFile(rootB.virtualFile.path))
        assertFalse(service.getLibraryRootsForFile(rootA.virtualFile.path).containsAll(service.getLibraryRoots(rootB.virtualFile.path)))
    }

    @Test
    fun testRootScopeIncludesDeclaredSourceOutsideProject() {
        val root = myFixture.addFileToProject(
            "RootA.java",
            "//SOURCES src/AHelper.java\nclass RootA { AHelper helper; }",
        )
        val helperPath = Files.createDirectories(Files.createTempDirectory("jbang-source").resolve("src"))
            .resolve("AHelper.java")
        Files.writeString(helperPath, "class AHelper {}")
        val helper = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(helperPath)!!
        installInfo(root.virtualFile.path, ScriptInfo(sources = listOf(
            dev.jbang.idea.cli.SourceEntry(helper.path, helper.path),
        )))
        fireLibraryChange(project)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        assertEquals(
            listOf(helper),
            JBangProjectService.getInstance(project).getSourceFilesForFile(root.virtualFile.path),
        )
        assertTrue(
            JBangResolveScopeEnlarger().getAdditionalResolveScope(root.virtualFile, project)!!.contains(helper)
        )
        myFixture.configureFromExistingVirtualFile(root.virtualFile)
        val reference = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            root,
            com.intellij.psi.PsiJavaCodeReferenceElement::class.java,
        ).single { it.text == "AHelper" }
        assertEquals(helper, reference.resolve()?.containingFile?.virtualFile)
    }

    @Test
    fun testRootDoesNotResolveSiblingSourcesDeclaredByAnotherRoot() {
        val rootA = myFixture.addFileToProject("RootA.java", "//SOURCES src/AHelper.java\nclass RootA { BHelper helper; }")
        val rootB = myFixture.addFileToProject("RootB.java", "//SOURCES src/BHelper.java\nclass RootB {}")
        val sourceDir = Files.createTempDirectory("jbang-isolated-sources")
        val helperA = Files.writeString(sourceDir.resolve("AHelper.java"), "class AHelper {}")
        val helperB = Files.writeString(sourceDir.resolve("BHelper.java"), "class BHelper {}")
        val fileA = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(helperA)!!
        val fileB = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(helperB)!!
        installInfo(rootA.virtualFile.path, ScriptInfo(sources = listOf(dev.jbang.idea.cli.SourceEntry(fileA.path, fileA.path))))
        installInfo(rootB.virtualFile.path, ScriptInfo(sources = listOf(dev.jbang.idea.cli.SourceEntry(fileB.path, fileB.path))))
        fireLibraryChange(project)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        myFixture.configureFromExistingVirtualFile(rootA.virtualFile)
        val reference = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            rootA,
            com.intellij.psi.PsiJavaCodeReferenceElement::class.java,
        ).single { it.text == "BHelper" }

        assertNull(reference.resolve())
    }

    @Test
    fun testRootScriptResolvesDeclaredSourceFile() {
        val root = myFixture.addFileToProject(
            "RootA.java",
            "//SOURCES src/AHelper.java\nclass RootA { AHelper helper; }",
        )
        val helper = myFixture.addFileToProject("src/AHelper.java", "class AHelper {}")
        installInfo(root.virtualFile.path, ScriptInfo(sources = listOf(
            dev.jbang.idea.cli.SourceEntry(helper.virtualFile.path, helper.virtualFile.path),
        )))
        myFixture.configureFromExistingVirtualFile(root.virtualFile)
        val reference = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            root,
            com.intellij.psi.PsiJavaCodeReferenceElement::class.java,
        ).single { it.text == "AHelper" }

        assertEquals(helper, reference.resolve()?.containingFile)
    }

    @Test
    fun testSourceReverseMapUsesOwningRootsClasspath() {
        val jar = Files.createTempFile("root-a", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val root = myFixture.addFileToProject("RootA.java", "//SOURCES AHelper.java\nclass RootA {}")
        val helper = myFixture.addFileToProject("AHelper.java", "class AHelper {}")
        installInfo(root.virtualFile.path, ScriptInfo(
            resolvedDependencies = listOf(jar.path),
            sources = listOf(
                dev.jbang.idea.cli.SourceEntry(root.virtualFile.path, root.virtualFile.path),
                dev.jbang.idea.cli.SourceEntry(helper.virtualFile.path, helper.virtualFile.path),
            )
        ))
        val service = JBangProjectService.getInstance(project)

        assertEquals(setOf(root.virtualFile.path), service.getOwningRoots(helper.virtualFile.path))
        assertEquals(service.getLibraryRoots(root.virtualFile.path), service.getLibraryRootsForFile(helper.virtualFile.path))
    }

    @Test
    fun testSourceResolveScopeIncludesOwningRootsDependencies() {
        val jar = Files.createTempFile("root-a", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use {
            it.putNextEntry(JarEntry("example/SourceDependency.class"))
            it.write(byteArrayOf(0))
            it.closeEntry()
        }
        val root = myFixture.addFileToProject("RootA.java", "//SOURCES AHelper.java\nclass RootA {}")
        val helper = myFixture.addFileToProject("AHelper.java", "class AHelper { example.SourceDependency value; }")
        installInfo(root.virtualFile.path, ScriptInfo(
            resolvedDependencies = listOf(jar.path),
            sources = listOf(dev.jbang.idea.cli.SourceEntry(helper.virtualFile.path, helper.virtualFile.path))
        ))
        val dependencyClass = JBangProjectService.getInstance(project).getLibraryRoots(root.virtualFile.path)
            .single().findFileByRelativePath("example/SourceDependency.class")!!

        assertTrue(JBangResolveScopeEnlarger().getAdditionalResolveScope(helper.virtualFile, project)!!.contains(dependencyClass))
    }

    @Test
    fun testSharedSourceUsesActiveOwningRoot() {
        val jarA = Files.createTempFile("root-a", ".jar").toFile()
        val jarB = Files.createTempFile("root-b", ".jar").toFile()
        JarOutputStream(jarA.outputStream()).use { }
        JarOutputStream(jarB.outputStream()).use { }
        val rootA = myFixture.addFileToProject("RootA.java", "//SOURCES Shared.java\nclass RootA {}")
        val rootB = myFixture.addFileToProject("RootB.java", "//SOURCES Shared.java\nclass RootB {}")
        val shared = myFixture.addFileToProject("Shared.java", "class Shared {}")
        val sourceA = dev.jbang.idea.cli.SourceEntry(shared.virtualFile.path, shared.virtualFile.path)
        installInfo(rootA.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jarA.path), sources = listOf(sourceA)))
        installInfo(rootB.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jarB.path), sources = listOf(sourceA)))
        val service = JBangProjectService.getInstance(project)

        service.setActiveRoot(rootB.virtualFile.path)
        assertEquals(setOf(rootA.virtualFile.path, rootB.virtualFile.path), service.getOwningRoots(shared.virtualFile.path))
        assertEquals(service.getLibraryRoots(rootB.virtualFile.path), service.getLibraryRootsForFile(shared.virtualFile.path))
    }

    @Test
    fun testDeletingRootEvictsItsLibraryAndWidgetState() {
        val jar = Files.createTempFile("jbang-overlay", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val script = myFixture.addFileToProject("overlay.java", "//DEPS example:overlay:1\nclass overlay {}")
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))
        val service = JBangProjectService.getInstance(project)
        service.setActiveRoot(script.virtualFile.path)

        JBangFileListener(project).after(listOf(VFileDeleteEvent(this, script.virtualFile)))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertTrue(service.allRoots.isEmpty())
        assertNull(service.activeRootPath)
        assertTrue(JBangLibraryProvider().getAdditionalProjectLibraries(project).isEmpty())
    }

    @Test
    fun testRenamingRootEvictsOldPathAndResolvesNewPath() {
        val jar = Files.createTempFile("jbang-overlay", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val script = myFixture.addFileToProject("old.java", "//DEPS example:overlay:1\nclass old {}")
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))
        val service = JBangProjectService.getInstance(project)
        service.setActiveRoot(script.virtualFile.path)

        val oldPath = script.virtualFile.path
        myFixture.renameElement(script, "renamed.java")
        JBangFileListener(project).after(listOf(
            com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent(
                this, script.virtualFile, VirtualFile.PROP_NAME, "old.java", "renamed.java"
            )
        ))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertNull("Old path should be evicted", service.getInfo(oldPath))
    }

    @Test
    fun testLibraryNodeIsNavigatableAndShowsRootPath() {
        val jar = Files.createTempFile("jbang-overlay", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val script = myFixture.addFileToProject("overlay.java", "//DEPS example:overlay:1\nclass overlay {}")
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))

        val lib = JBangLibraryProvider().getAdditionalProjectLibraries(project).single()
        assertInstanceOf(lib, Navigatable::class.java)

        val presentation = (lib as com.intellij.navigation.ItemPresentation)
        assertEquals("jbang: overlay.java", presentation.presentableText)
    }

    @Test
    fun testLibrariesWithSameScriptNameInDifferentDirsAreDistinct() {
        val jar = Files.createTempFile("jbang-dup", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val scriptA = myFixture.addFileToProject("a/resource.java", "//DEPS x:y:1\nclass resource {}")
        val scriptB = myFixture.addFileToProject("b/resource.java", "//DEPS x:z:1\nclass resource {}")
        installInfo(scriptA.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))
        installInfo(scriptB.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))

        val libs = JBangLibraryProvider().getAdditionalProjectLibraries(project)
        assertEquals(2, libs.size)
        val names = libs.map { (it as com.intellij.navigation.ItemPresentation).presentableText }.toSet()
        assertEquals("Library names should be unique", 2, names.size)
    }

    @Test
    fun testLibraryNodeCanNavigateToRealFile() {
        val jar = Files.createTempFile("jbang-nav", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val scriptPath = Files.writeString(Files.createTempFile("NavRoot", ".java"), "//DEPS x:y:1\nclass NavRoot {}")
        val scriptFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(scriptPath)!!
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(testRootDisposable, scriptPath.toRealPath().parent.toString())
        installInfo(scriptFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))

        val lib = JBangLibraryProvider().getAdditionalProjectLibraries(project).single()
        assertTrue((lib as Navigatable).canNavigate())
    }

    @Test
    fun testUnchangedLibrariesAreNotReannounced() {
        val jar = Files.createTempFile("jbang-overlay", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val script = myFixture.addFileToProject("overlay.java", "//DEPS example:overlay:1\nclass overlay {}")
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))

        var changeCount = 0
        var completedCount = 0
        project.messageBus.connect(testRootDisposable).subscribe(
            AdditionalLibraryRootsListener.TOPIC,
            AdditionalLibraryRootsListener { _, _, _, _ -> changeCount++ },
        )

        fireLibraryChange(project) { completedCount++ }
        repeat(3) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
        assertEquals(1, completedCount)

        fireLibraryChange(project) { completedCount++ }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        repeat(3) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
        assertEquals(2, completedCount)
        assertEquals("Identical roots must not invalidate PSI twice", 1, changeCount)
    }

    @Test
    fun testLibraryChangeAnnouncesRootsForIndexing() {
        val jar = Files.createTempFile("jbang-overlay", ".jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val script = myFixture.addFileToProject("overlay.java", "//DEPS example:overlay:1\nclass overlay {}")
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))

        var announcedRoots: Collection<VirtualFile> = emptyList()
        project.messageBus.connect(testRootDisposable).subscribe(
            AdditionalLibraryRootsListener.TOPIC,
            AdditionalLibraryRootsListener { _, _, newRoots, _ -> announcedRoots = newRoots }
        )

        fireLibraryChange(project)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertTrue("Changed roots must be announced so IntelliJ indexes them", announcedRoots.isNotEmpty())
    }
}
