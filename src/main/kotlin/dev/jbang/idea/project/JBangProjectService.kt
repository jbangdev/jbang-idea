package dev.jbang.idea.project

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiManager
import dev.jbang.idea.cli.JBangCli
import dev.jbang.idea.cli.ScriptInfo
import dev.jbang.idea.debug
import dev.jbang.idea.jbangLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-project service that caches jbang script metadata.
 * Maps root script paths → resolved ScriptInfo from `jbang info tools`.
 */
@Service(Service.Level.PROJECT)
class JBangProjectService(private val project: Project) {

    private val log = jbangLog<JBangProjectService>()

    /** Cached script info keyed by absolute file path. */
    private val cache = ConcurrentHashMap<String, ScriptInfo>()
    private val libraryRoots = ConcurrentHashMap<String, List<VirtualFile>>()
    private val sourceOwners = ConcurrentHashMap<String, MutableSet<String>>()

    @Volatile
    var syncingRootPath: String? = null
        private set

    @Volatile
    var lastFailedRootPath: String? = null
        private set

    @Volatile
    var lastSucceededRootPath: String? = null
        private set

    @Volatile
    var lastSyncErrors: List<String> = emptyList()
        private set

    val lastSyncErrorCount: Int
        get() = lastSyncErrors.size

    /** The currently active root script path (for classpath switching). */
    @Volatile
    var activeRootPath: String? = null
        private set

    val activeScriptInfo: ScriptInfo?
        get() = activeRootPath?.let { cache[it] }

    /** All known root scripts and their info. */
    val allRoots: Map<String, ScriptInfo>
        get() = cache.toMap()

    /**
     * Resolve (or re-resolve) a root script's metadata via the jbang CLI.
     * Runs the CLI call — should be called from a background thread.
     */
    fun resolve(file: VirtualFile, deferStatusCompletion: Boolean = false): ScriptInfo? {
        val path = file.path
        syncStarted(path)
        var succeeded = false
        return try {
            val info = JBangCli.resolveScriptInfo(path, project)
            if (info != null) {
                JBangJdkSync.register(info)
                cacheResolved(path, info, info.classpathJars.mapNotNull {
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(java.io.File(it))
                        ?.let(JarFileSystem.getInstance()::getJarRootForLocalFile)
                })
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) PsiManager.getInstance(project).findFile(file)?.let {
                        DaemonCodeAnalyzer.getInstance(project).restart(it, this)
                    }
                }
                log.debug { "Resolved jbang script: $path (${info.resolvedDependencies.size} deps, java=${info.javaVersion})" }
                succeeded = info.resolutionErrors.isEmpty()
            } else {
                cache.remove(path)
                libraryRoots.remove(path)
            }
            info
        } finally {
            if (!deferStatusCompletion) {
                val errors = cache[path]?.resolutionErrors.orEmpty().ifEmpty {
                    if (succeeded) emptyList() else listOf("jbang info tools failed; see idea.log for command output")
                }
                syncFinished(path, succeeded, errors)
            }
        }
    }

    internal fun syncStarted(path: String) {
        syncingRootPath = path
        lastFailedRootPath = null
        lastSucceededRootPath = null
        lastSyncErrors = emptyList()
        updateWidget()
    }

    internal fun syncFinished(path: String, succeeded: Boolean, errors: List<String> = emptyList()) {
        if (syncingRootPath == path) syncingRootPath = null
        lastFailedRootPath = path.takeUnless { succeeded }
        lastSucceededRootPath = path.takeIf { succeeded }
        lastSyncErrors = errors
        updateWidget()
        if (!succeeded && errors.isNotEmpty() && dev.jbang.idea.settings.JBangSettings.instance.notifySyncErrors) {
            ApplicationManager.getApplication().invokeLater({
                if (!project.isDisposed) {
                    NotificationGroupManager.getInstance().getNotificationGroup("JBang")
                        .createNotification(
                            "Failed to sync ${java.io.File(path).name}",
                            errors.joinToString("<br>") { StringUtil.escapeXmlEntities(it) },
                            NotificationType.ERROR,
                        ).notify(project)
                }
            }, ModalityState.nonModal())
        }
    }

    private fun updateWidget() {
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) WindowManager.getInstance().getStatusBar(project)?.updateWidget("JBangActiveRoot")
        }, ModalityState.nonModal())
    }

    internal fun cacheResolved(path: String, info: ScriptInfo, roots: List<VirtualFile>) {
        removeSourceOwnership(path)
        cache[path] = info
        libraryRoots[path] = roots
        if (activeRootPath == path) JBangJdkSync.applyToStandaloneProject(project, info)
        info.sources.asSequence()
            .filter { it.error == null }
            .flatMap { sequenceOf(it.originalResource, it.backingResource) }
            .filter { it.isNotBlank() && it != path }
            .forEach { sourceOwners.computeIfAbsent(it) { ConcurrentHashMap.newKeySet() }.add(path) }
    }

    /** Set the active root (triggers classpath switch). */
    fun setActiveRoot(path: String) {
        activeRootPath = path
        cache[path]?.let { JBangJdkSync.applyToStandaloneProject(project, it) }
        fireLibraryChange(project)
        updateWidget()
    }

    /** Get cached info for a script path, or null. */
    fun getInfo(path: String): ScriptInfo? = cache[path]

    fun getLibraryRoots(path: String): List<VirtualFile> = libraryRoots[path].orEmpty()

    fun getOwningRoots(sourcePath: String): Set<String> = sourceOwners[sourcePath]?.toSet().orEmpty()

    fun getLibraryRootsForFile(path: String): List<VirtualFile> = rootForFile(path)?.let(::getLibraryRoots).orEmpty()

    internal fun allSourceFiles(): Set<VirtualFile> = cache.keys.flatMapTo(mutableSetOf(), ::getSourceFilesForFile)

    fun getSourceFilesForFile(path: String): List<VirtualFile> = rootForFile(path)?.let(cache::get)?.sources.orEmpty()
        .asSequence()
        .filter { it.error == null }
        .map { it.backingResource }
        .filter { it.isNotBlank() }
        .mapNotNull(LocalFileSystem.getInstance()::findFileByPath)
        .toList()

    private fun rootForFile(path: String): String? {
        if (cache.containsKey(path)) return path
        val owners = getOwningRoots(path)
        return activeRootPath?.takeIf(owners::contains) ?: owners.singleOrNull()
    }

    /** Remove a script from the cache (e.g., file deleted). */
    fun evict(path: String) {
        cache.remove(path)
        libraryRoots.remove(path)
        removeSourceOwnership(path)
        if (activeRootPath == path) activeRootPath = null
    }

    /** Clear everything. */
    fun clear() {
        cache.clear()
        libraryRoots.clear()
        sourceOwners.clear()
        activeRootPath = null
        syncingRootPath = null
        lastFailedRootPath = null
        lastSucceededRootPath = null
        lastSyncErrors = emptyList()
    }

    private fun removeSourceOwnership(rootPath: String) {
        sourceOwners.entries.removeIf { (_, owners) ->
            owners.remove(rootPath)
            owners.isEmpty()
        }
    }

    companion object {
        fun getInstance(project: Project): JBangProjectService =
            project.getService(JBangProjectService::class.java)
    }
}
