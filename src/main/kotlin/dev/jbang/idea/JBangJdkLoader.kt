package dev.jbang.idea

import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator


class JBangJdkLoader : PreloadingActivity() {
    override fun preload(indicator: ProgressIndicator) {
        JBangJdkService().synchJBangJdksWithIdea();
    }
}