package dev.jbang.idea

import com.intellij.openapi.diagnostic.Logger

/**
 * Logging convention for the JBang plugin.
 *
 * Usage in any class:
 *   private val log = jbangLog<MyClass>()
 *
 * Log levels:
 *   log.debug { "expensive message: $details" }  — only evaluated when debug enabled
 *   log.info("simple message")                    — normal operation milestones
 *   log.warn("problem", exception)                — recoverable errors
 *   log.error("broken", exception)                — bugs
 *
 * Enabling debug at runtime (no restart needed):
 *   Help → Diagnostic Tools → Debug Log Settings → add:
 *     #dev.jbang.idea
 *   This enables debug for all classes under dev.jbang.idea.
 *   Or use a specific class:
 *     #dev.jbang.idea.run.JBangTerminalRunState
 *
 * The # prefix is IntelliJ's convention — it maps to Logger category names.
 */
inline fun <reified T> jbangLog(): Logger = Logger.getInstance(T::class.java)

/**
 * Lazy debug — lambda only evaluated when debug logging is enabled.
 * Zero overhead in production.
 */
inline fun Logger.debug(lazyMessage: () -> String) {
    if (isDebugEnabled) {
        debug(lazyMessage())
    }
}
