package dev.jbang.idea.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import dev.jbang.idea.settings.JBangSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import dev.jbang.idea.debug
import dev.jbang.idea.jbangLog

private val log = jbangLog<JBangStartupActivity>()

/**
 * On project open, scans for jbang root scripts and resolves them.
 * Also listens for file changes and editor focus to trigger re-sync.
 */
class JBangStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) return

        // Wait for indexing to finish, then use the persisted index
        com.intellij.openapi.project.DumbService.getInstance(project).waitForSmartMode()

        val service = JBangProjectService.getInstance(project)
        val roots = JBangScriptFileIndex.findRootScripts(project)
        log.debug { "Found ${roots.size} jbang root scripts from index" }

        for (file in roots) {
            service.resolve(file)
        }

        if (service.activeRootPath == null) {
            service.allRoots.keys.firstOrNull()?.let { service.setActiveRoot(it) }
        }

        if (service.allRoots.isNotEmpty()) {
            fireLibraryChange(project)
        }
    }
}

/**
 * Listens for file saves and triggers re-sync for jbang scripts.
 */
class JBangFileListener(private val project: Project) : BulkFileListener {

    private fun updateRunConfigs(oldPath: String, newPath: String) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val runManager = com.intellij.execution.RunManager.getInstance(project)
            for (settings in runManager.allSettings) {
                val config = settings.configuration as? dev.jbang.idea.run.JBangRunConfiguration ?: continue
                if (config.scriptPath == oldPath) {
                    config.scriptPath = newPath
                    config.name = "jbang ${java.io.File(newPath).name}"
                }
            }
        }
    }

    override fun after(events: List<VFileEvent>) {
        val service = JBangProjectService.getInstance(project)

        for (event in events) {
            when (event) {
                is VFileDeleteEvent -> if (service.getInfo(event.path) != null) {
                    service.evict(event.path)
                    fireLibraryChange(project)
                }
                is com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent -> {
                    if (event.propertyName == VirtualFile.PROP_NAME) {
                        val oldPath = event.oldPath
                        val newPath = event.file.path
                        if (service.getInfo(oldPath) != null) {
                            service.evict(oldPath)
                            updateRunConfigs(oldPath, newPath)
                            if (JBangScriptDetector.isRootScript(event.file)) {
                                service.scope.launch {
                                    service.resolve(event.file)
                                    fireLibraryChange(project)
                                }
                            } else {
                                fireLibraryChange(project)
                            }
                        }
                    }
                }
                is com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent -> {
                    val oldPath = event.oldPath
                    val newPath = event.file.path
                    if (service.getInfo(oldPath) != null) {
                        service.evict(oldPath)
                        updateRunConfigs(oldPath, newPath)
                        if (JBangScriptDetector.isRootScript(event.file)) {
                            service.scope.launch {
                                service.resolve(event.file)
                                fireLibraryChange(project)
                            }
                        } else {
                            fireLibraryChange(project)
                        }
                    }
                }
                is VFileContentChangeEvent -> {
                    if (!JBangSettings.instance.autoSync) continue
                    val file = event.file
                    if (!JBangScriptDetector.isScriptExtension(file)) continue
                    if (JBangScriptDetector.isRootScript(file)) {
                        service.scope.launch {
                            service.resolve(file)
                            fireLibraryChange(project)
                        }
                    } else if (service.getInfo(file.path) != null) {
                        service.evict(file.path)
                        fireLibraryChange(project)
                    }
                }
            }
        }
    }
}

/**
 * When a file is opened in the editor, auto-switch active root if it's a jbang script.
 */
class JBangEditorListener(private val project: Project) : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (!JBangScriptDetector.isScriptExtension(file)) return

        val service = JBangProjectService.getInstance(project)

        if (JBangScriptDetector.isRootScript(file)) {
            val path = file.path
            log.debug { "Editor opened jbang root: $path" }
            if (service.getInfo(path) == null) {
                service.scope.launch {
                    service.resolve(file)
                    service.setActiveRoot(path)
                    fireLibraryChange(project)
                }
            } else {
                service.setActiveRoot(path)
            }
        }
        else {
            val owners = service.getOwningRoots(file.path)
            (owners.singleOrNull() ?: service.activeRootPath?.takeIf(owners::contains))?.let {
                service.setActiveRoot(it)
                WindowManager.getInstance().getStatusBar(project)?.updateWidget("JBangActiveRoot")
            }
        }
    }
}

private val announcedLibraryRoots = Key.create<Collection<VirtualFile>>("jbang.announcedLibraryRoots")

internal fun fireLibraryChange(project: Project, completed: () -> Unit = {}) {
    if (project.isDisposed) return
    ApplicationManager.getApplication().invokeLater({
        if (project.isDisposed) return@invokeLater
        val newRoots = JBangLibraryProvider().getAdditionalProjectLibraries(project)
            .flatMap { it.binaryRoots + it.sourceRoots }
        val oldRoots = project.getUserData(announcedLibraryRoots).orEmpty()
        com.intellij.openapi.application.runWriteAction {
            if (project.isDisposed) return@runWriteAction
            JBangKotlinLibraryMirror.update(project)
            if (oldRoots.toSet() != newRoots.toSet()) {
                project.putUserData(announcedLibraryRoots, newRoots)
                AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
                    project, /* presentableLibraryName = */ "JBang",
                    oldRoots, newRoots, /* libraryNameForDebug = */ "JBang"
                )
            }
            WindowManager.getInstance().getStatusBar(project)?.updateWidget("JBangActiveRoot")
            completed()
        }
    }, ModalityState.nonModal())
}
