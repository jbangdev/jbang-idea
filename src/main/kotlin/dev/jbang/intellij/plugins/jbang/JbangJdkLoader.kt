package dev.jbang.intellij.plugins.jbang

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File


class JbangJdkLoader : PreloadingActivity() {
    override fun preload(indicator: ProgressIndicator) {
        val jbangJdks = jbangJdks()
        if (jbangJdks.isNotEmpty()) {
            val javaSdkVersions = ProjectJdkTable.getInstance()
                .getSdksOfType(JavaSdk.getInstance())
                .map { it.name }
            for (jdk in jbangJdks) {
                val jdkVersion = jdk.key
                if (!javaSdkVersions.contains(jdk.key)) {
                    val sdkHome = LocalFileSystem.getInstance().findFileByIoFile(jdk.value)!!
                    val newSdk = SdkConfigurationUtil.setupSdk(arrayOf(), sdkHome, JavaSdk.getInstance(), true, null, jdkVersion)
                    if (newSdk != null) {
                        ApplicationManager.getApplication().invokeLater {
                            SdkConfigurationUtil.addSdk(newSdk)
                        }
                    }
                }
            }
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
                if (File(jdkDir, "bin/java").exists()) {
                    jdkMap[jdkVersion] = jdkDir
                }
            }
        }
    }
    return jdkMap
}

