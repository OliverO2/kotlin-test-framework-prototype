package testFramework.internal

import testFramework.TestElement

/**
 * A [TestElement.Selection] based on lists of [includePatterns] and [excludePatterns].
 */
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

        private val REGEX_META_CHARACTERS = "\\[].^$?+{}|()".toSet()
    }
}

/**
 * A [TestElement.Selection] created from command line arguments which define [includePatterns] and [excludePatterns].
 */
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

/**
 * A [TestElement.Selection] created from environment variables which define [includePatterns] and [excludePatterns].
 */
internal class EnvironmentBasedElementSelection(includePatterns: String?, excludePatterns: String?) :
    ListsBasedElementSelection(includePatterns, excludePatterns)
