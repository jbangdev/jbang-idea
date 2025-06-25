package dev.jbang.idea.ui

import javax.swing.JTextArea

class UsagePanel constructor(prefix: String) : JTextArea() {
    private val jbangUsage = """- Create a JBang file, such as Kotlin, Java, or Groovy file.
- You might need to install JBang from https://www.jbang.dev/download/
- A new file can be created from a template (File | New | JBang Script)
- JBang script should contain `///usr/bin/env jbang "${'$'}0" "${'$'}@" ; exit ${'$'}?`
- JBang script code can be placed anywhere

Features:
- JSON Schema for jbang-catalog.json
- JDKs sync with JBang: sync JDKs from JBang to IntelliJ IDEA
- JBang directives completion
- Sync Dependencies between JBang and Gradle
- Sync Dependencies to IDEA's module by right-clicking in the JBang script header area and choosing "Sync JBang DEPS to Module"

References:
- Usage: https://www.jbang.dev/documentation/guide/latest/usage.html
- Dependencies: https://www.jbang.dev/documentation/guide/latest/dependencies.html
- Catalogs: https://www.jbang.dev/documentation/guide/latest/alias_catalogs.html 
"""

    init {
        isOpaque = false
        text = prefix + jbangUsage
        isEditable = false
    }
}
