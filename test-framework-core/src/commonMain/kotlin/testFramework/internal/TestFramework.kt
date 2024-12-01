package testFramework.internal

import kotlinx.coroutines.test.TestScope
import testFramework.AbstractTestSession
import testFramework.AbstractTestSuite
import testFramework.TestElement
import testFramework.TestSession
import testFramework.TestSuite
import kotlin.time.Duration

/**
 * Initialize the test framework with a [TestSession].
 *
 * The framework invokes this function before creating any top-level test suites.
 */
internal fun initializeTestFramework(testSession: AbstractTestSession?, arguments: Array<String>? = null) {
    if (!arguments.isNullOrEmpty()) {
        argumentsBasedElementSelection = ArgumentsBasedElementSelection(arguments)
    }
    if (testSession == null) TestSession()
}

/**
 * Discover and execute tests.
 *
 * The framework invokes this function with a list of top-level [TestSuite]s.
 */
internal expect suspend fun runTests(suites: Array<AbstractTestSuite>)

internal var argumentsBasedElementSelection: TestElement.Selection? = null

internal open class ListsBasedElementSelection protected constructor(
    private val includePatterns: List<Regex>,
    private val excludePatterns: List<Regex>
) : TestElement.Selection {
    protected constructor(includePatterns: String?, excludePatterns: String?) :
        this(includePatterns.toRegexList(), excludePatterns.toRegexList())

    private var used = false

    override fun includes(testElement: TestElement): Boolean {
        if (!used) {
            logInfo { "Tests selected via $this" }
            used = true
        }

        return (includePatterns.isEmpty() || includePatterns.any { it.matches(testElement.elementPath) }) &&
            excludePatterns.none { it.matches(testElement.elementPath) }
    }

    override fun toString(): String =
        "${this::class.simpleName}(includePatterns=$includePatterns, excludePatterns=$excludePatterns)"

    companion object {
        /**
         * Returns a list of regular expressions from a string of comma-separated patterns with `*` wildcards.
         */
        private fun String?.toRegexList(): List<Regex> = this?.split(',')?.map {
            try {
                buildString {
                    for (character in it) {
                        when (character) {
                            '*' -> append(".*")
                            in REGEX_META_CHARACTERS -> append("\\$character")
                            else -> append(character)
                        }
                    }
                }.toRegex()
            } catch (throwable: Throwable) {
                throw IllegalArgumentException("Could not convert regex pattern '$it'.", throwable)
            }
        } ?: listOf()

        private val REGEX_META_CHARACTERS = "\\[].^${'$'}?+{}|()".toSet()
    }
}

internal class ArgumentsBasedElementSelection(arguments: Array<String>) :
    ListsBasedElementSelection(
        includePatterns = arguments.optionValue("include"),
        excludePatterns = arguments.optionValue("exclude")
    ) {
    companion object {
        private fun Array<String>.optionValue(optionName: String): String? {
            val optionNameIndex = indexOfFirst { it == "--$optionName" }
            return if (optionNameIndex >= 0) getOrNull(optionNameIndex + 1) else null
        }
    }
}

internal class EnvironmentBasedElementSelection(includePatterns: String?, excludePatterns: String?) :
    ListsBasedElementSelection(includePatterns, excludePatterns)

internal enum class LogLevel { DEBUG, INFO, ERROR }

private val logLevel = LogLevel.INFO

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
    if (messageLevel >= logLevel) println(message())
}

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

internal inline fun <R> configureTestsCatching(action: () -> R): Result<R> =
    runCatchingLogging("Could not configure tests.", action)

internal inline fun <R> executeTestsCatching(action: () -> R): Result<R> =
    runCatchingLogging("Test framework failure during execution.", action)

private inline fun <R> runCatchingLogging(headline: String, action: () -> R): Result<R> = runCatching {
    action()
}.onFailure { throwable ->
    throwable.logErrorWithStacktrace(headline)
}

internal expect suspend fun TestScope.runTestAwaitingCompletion(timeout: Duration, action: suspend TestScope.() -> Unit)
