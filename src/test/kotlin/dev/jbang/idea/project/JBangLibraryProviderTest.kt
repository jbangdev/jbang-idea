package dev.jbang.idea.project

import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.pom.Navigatable
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.jbang.idea.cli.ScriptInfo
import dev.jbang.idea.settings.JBangSettings
import org.junit.Test
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
    fun testProviderDoesNotLoadUncachedClasspathPathsOnEdt() {
        val jar = Files.createDirectories(Files.createTempDirectory("jbang-overlay").resolve("deep/maven/path"))
            .resolve("overlay.jar").toFile()
        JarOutputStream(jar.outputStream()).use { }
        val script = myFixture.addFileToProject("overlay.java", "//DEPS example:overlay:1\nclass overlay {}")
        installInfo(script.virtualFile.path, ScriptInfo(resolvedDependencies = listOf(jar.path)))

        JBangLibraryProvider().getAdditionalProjectLibraries(project)
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
        com.intellij.openapi.project.DumbService.getInstance(project).waitForSmartMode()
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
        com.intellij.openapi.project.DumbService.getInstance(project).waitForSmartMode()
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
        com.intellij.openapi.project.DumbService.getInstance(project).waitForSmartMode()
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
