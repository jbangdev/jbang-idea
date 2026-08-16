package dev.jbang.idea.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
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

        // Initial scan: find jbang root scripts in the project
        val projectDir = project.basePath?.let {
            VirtualFileManager.getInstance().findFileByUrl("file://$it")
        } ?: return

        val service = JBangProjectService.getInstance(project)
        log.debug { "Scanning project for jbang scripts: ${project.basePath}" }
        scanDirectory(projectDir, service)
        log.debug { "Found ${service.allRoots.size} jbang root scripts" }

        // Set the first root as active if none is set
        if (service.activeRootPath == null) {
            service.allRoots.keys.firstOrNull()?.let { service.setActiveRoot(it) }
        }

        // Notify library change after scan completes
        if (service.allRoots.isNotEmpty()) {
            fireLibraryChange(project)
        }
    }

    private fun scanDirectory(dir: VirtualFile, service: JBangProjectService) {
        for (child in dir.children) {
            if (child.isDirectory) {
                // ponytail: skip common non-source dirs, scan deeper later if needed
                if (child.name.startsWith(".") || child.name == "build" || child.name == "target" || child.name == "node_modules") continue
                scanDirectory(child, service)
            } else if (JBangScriptDetector.isRootScript(child)) {
                service.resolve(child)
            }
        }
    }
}

/**
 * Listens for file saves and triggers re-sync for jbang scripts.
 */
class JBangFileListener(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val service = JBangProjectService.getInstance(project)
        var changed = false

        for (event in events) {
            when (event) {
                is VFileDeleteEvent -> if (service.getInfo(event.path) != null) {
                    service.evict(event.path)
                    fireLibraryChange(project)
                    changed = true
                }
                is com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent -> {
                    if (event.propertyName == VirtualFile.PROP_NAME) {
                        val oldPath = event.oldPath
                        if (service.getInfo(oldPath) != null) {
                            service.evict(oldPath)
                            val newFile = event.file
                            if (JBangScriptDetector.isRootScript(newFile)) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    service.resolve(newFile)
                                    fireLibraryChange(project)
                                }
                            } else {
                                fireLibraryChange(project)
                            }
                            changed = true
                        }
                    }
                }
                is com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent -> {
                    val oldPath = event.oldPath
                    if (service.getInfo(oldPath) != null) {
                        service.evict(oldPath)
                        val newFile = event.file
                        if (JBangScriptDetector.isRootScript(newFile)) {
                            CoroutineScope(Dispatchers.IO).launch {
                                service.resolve(newFile)
                                fireLibraryChange(project)
                            }
                        } else {
                            fireLibraryChange(project)
                        }
                        changed = true
                    }
                }
                is VFileContentChangeEvent -> {
                    if (!JBangSettings.instance.autoSync) continue
                    val file = event.file
                    if (!JBangScriptDetector.isScriptExtension(file)) continue
                    if (JBangScriptDetector.isRootScript(file)) {
                        CoroutineScope(Dispatchers.IO).launch {
                            service.resolve(file)
                            fireLibraryChange(project)
                        }
                        changed = true
                    } else if (service.getInfo(file.path) != null) {
                        service.evict(file.path)
                        fireLibraryChange(project)
                        changed = true
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
                CoroutineScope(Dispatchers.IO).launch {
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
    com.intellij.openapi.project.DumbService.getInstance(project).smartInvokeLater {
        if (project.isDisposed) return@smartInvokeLater
        val newRoots = JBangLibraryProvider().getAdditionalProjectLibraries(project)
            .flatMap { it.binaryRoots + it.sourceRoots }
        val oldRoots = project.getUserData(announcedLibraryRoots).orEmpty()
        com.intellij.openapi.application.runWriteAction {
            if (!project.isDisposed) {
                project.putUserData(announcedLibraryRoots, newRoots)
                AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
                    project, /* presentableLibraryName = */ "JBang",
                    oldRoots, newRoots, /* libraryNameForDebug = */ "JBang"
                )
                WindowManager.getInstance().getStatusBar(project)?.updateWidget("JBangActiveRoot")
                completed()
            }
        }
    }
}
