package dev.jbang.idea

import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity


class JBangJdkLoader : ProjectActivity {

    override suspend fun execute(project: Project) {
        RunOnceUtil.runOnceForApp("JBangJdkLoader") {
            JBangJdkService.syncJBangJdksWithIdea()
        }
    }
}