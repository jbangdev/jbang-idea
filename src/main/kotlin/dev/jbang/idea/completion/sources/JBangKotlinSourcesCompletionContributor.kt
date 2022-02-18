package dev.jbang.idea.completion.sources

import dev.jbang.idea.kotlinIcon
import org.jetbrains.kotlin.idea.KotlinLanguage
import javax.swing.Icon


class JBangKotlinSourcesCompletionContributor : JBangBaseSourcesCompletionContributor(KotlinLanguage.INSTANCE) {

    override fun allowedExtName(): String {
        return ".kt"
    }


    override fun fileTypeIcon(): Icon {
        return kotlinIcon
    }
}