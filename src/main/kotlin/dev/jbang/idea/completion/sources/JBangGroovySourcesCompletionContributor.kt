package dev.jbang.idea.completion.sources

import dev.jbang.idea.groovyIcon
import org.jetbrains.plugins.groovy.GroovyLanguage
import javax.swing.Icon


class JBangGroovySourcesCompletionContributor : JBangBaseSourcesCompletionContributor(GroovyLanguage) {

    override fun allowedExtName(): String {
        return ".groovy"
    }


    override fun fileTypeIcon(): Icon {
        return groovyIcon
    }
}