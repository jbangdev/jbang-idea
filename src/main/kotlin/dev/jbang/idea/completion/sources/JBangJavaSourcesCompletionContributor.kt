package dev.jbang.idea.completion.sources

import com.intellij.icons.AllIcons
import com.intellij.lang.java.JavaLanguage
import javax.swing.Icon


class JBangJavaSourcesCompletionContributor : JBangBaseSourcesCompletionContributor(JavaLanguage.INSTANCE) {

    override fun allowedExtName(): String {
        return ".java"
    }


    override fun fileTypeIcon(): Icon {
        return AllIcons.FileTypes.Java
    }
}