package dev.jbang.idea

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import com.intellij.openapi.util.SystemInfo


/**
 * Service that deals with JBang managed JDKs and IDEA based SDKs.
 */
class JBangJdkService {

    val LOGGER: Logger = Logger.getInstance(JBangJdkService::class.java)

    /**
     * Synchronizes JBang managed JDKs with IDEA.
     */
    fun synchJBangJdksWithIdea() {
        LOGGER.info("Synchronizing JBang managed JDKs with IDEA.")
        val jbangJdks = jbangJdks()
        if (jbangJdks.isNotEmpty()) {
            val javaSdkVersions = ProjectJdkTable.getInstance()
                .getSdksOfType(JavaSdk.getInstance())
                .map { it.name }
            for (jdk in jbangJdks) {
                val jdkVersion = jdk.key
                if (!javaSdkVersions.contains(jdk.key)) {
                    syncJdkWithIdeaInBackground(jdk.value, jdkVersion)
                }
            }
        }
    }

    /**
     * Synchronizes a given JDK path with IDEA in the background.
     */
    fun syncJdkWithIdeaInBackground(jdkPath: File, jdkVersion: String) {
        syncJdkWithIdea(jdkPath, jdkVersion, true)
    }

    /**
     * Synchronizes a given JDK path with the IDEA and notifies the user about that.
     */
    fun syncJdkWithIdeaWithProject(jdkPath: File, jdkVersion: String, project: Project) {
        syncJdkWithIdea(jdkPath, jdkVersion, false, project)
        val jbangNotificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_INFO)
        jbangNotificationGroup.createNotification(
            "JBang managed JDK ($jdkVersion) just synchronized with IDEA.",
            NotificationType.INFORMATION
        ).notify(project)
    }

    /**
     * Returns the path of a given JBang managed JDK.
     *
     * If not exists than a null being returned.
     */
    fun getJbangJdkPath(version: String): File? {
        return jbangJdks()[version]
    }

    private fun syncJdkWithIdea(jdkPath: File, jdkVersion: String, inBackground: Boolean, project: Project? = null) {
        LOGGER.info("Synchronizing JBang managed JDK at ${jdkPath.path} with version: $jdkVersion")
        val sdkHome = LocalFileSystem.getInstance().findFileByIoFile(jdkPath)!!
        val newSdk =
            SdkConfigurationUtil.setupSdk(arrayOf(), sdkHome, JavaSdk.getInstance(), true, null, jdkVersion)
        if (newSdk != null) {
            if (inBackground) {
                ApplicationManager.getApplication().invokeLater {
                    SdkConfigurationUtil.addSdk(newSdk)
                }
            } else {
                SdkConfigurationUtil.addSdk(newSdk)
            }
        }
    }

    private fun jbangJdks(): Map<String, File> {
        val jdkMap = HashMap<String, File>()
        val jbangHome = File(File(System.getProperty("user.home")), ".jbang")
        if (jbangHome.exists()) {
            val jdks = File(jbangHome, "cache/jdks")
            if (jdks.exists()) {
                jdks.list()?.forEach {
                    val jdkVersion = it
                    val jdkDir = File(jdks, jdkVersion)
                    val java = if (SystemInfo.isWindows) {
                        "bin/java.exe"
                    } else {
                        "bin/java"
                    }
                    if (File(jdkDir, java).exists()) {
                        jdkMap[jdkVersion] = jdkDir
                    }
                }
            }
        }
        return jdkMap
    }

}



