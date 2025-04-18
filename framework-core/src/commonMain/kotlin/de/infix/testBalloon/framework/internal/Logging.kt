package de.infix.testBalloon.framework.internal

import de.infix.testBalloon.framework.testPlatform
import kotlinx.datetime.Clock

enum class LogLevel { DEBUG, INFO, ERROR }

var logLevel = LogLevel.INFO

internal fun logDebug(message: () -> String) {
    log(LogLevel.DEBUG) { "DEBUG: ${message()}" }
}

internal fun logInfo(message: () -> String) {
    log(LogLevel.INFO) { "INFO: ${message()}" }
}

internal fun logError(message: () -> String) {
    log(LogLevel.ERROR) { "ERROR: ${message()}" }
}

internal fun log(messageLevel: LogLevel, message: () -> String) {
    if (messageLevel >= logLevel) {
        printlnFixed("${Clock.System.now()} [${testPlatform.threadId()}] ${message()}")
    }
}

/**
 * WORKAROUND https://youtrack.jetbrains.com/issue/KT-48292 – KJS / IR: `println` doesn't move to a new line in tests
 */
expect fun printlnFixed(message: Any?)

internal fun Throwable.logErrorWithStacktrace(headline: String, includeStacktrace: Boolean = true) {
    logError {
        buildString {
            append(headline)
            message?.let { primaryMessage ->
                append("\n\t$primaryMessage")
                cause?.let { cause ->
                    append("\n")
                    append(cause.toString().prependIndent("\t"))
                }
            }
            if (includeStacktrace) {
                append("\n\tStack trace:\n")
                append(stackTraceToString().prependIndent("\t\t"))
            }
        }
    }
}

/**
 * Performs the test configuration [action], catching any error and logging it.
 */
internal inline fun <R> configureTestsCatching(action: () -> R): Result<R> =
    runCatchingLogging("Could not configure tests.", action)

/**
 * Executes tests in [action], catching any error and logging it.
 */
internal inline fun <R> executeTestsCatching(action: () -> R): Result<R> =
    runCatchingLogging("Test framework failure during execution.", action)

/**
 * Runs [action], catching any error and logging it with a [headline].
 */
private inline fun <R> runCatchingLogging(headline: String, action: () -> R): Result<R> = runCatching {
    action()
}.onFailure { throwable ->
    throwable.logErrorWithStacktrace(headline)
}
