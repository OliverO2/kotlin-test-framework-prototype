package testFramework.internal

import kotlinx.coroutines.test.TestResult
import testFramework.TestElement
import testFramework.assertMessageStartsWith
import testFramework.reference
import testFramework.testSuite
import testFramework.withTestFramework
import testFramework.withTestReport
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class TestFrameworkTests {
    @Test
    fun frameworkNotInitialized() {
        assertFailsWith<IllegalStateException> {
            val suite by testSuite("default configuration") {}
            suite.reference()
        }.assertMessageStartsWith("The test framework was not initialized.")
    }

    @Test
    fun elementSelectionByArguments() = verifyElementSelection(
        ArgumentsBasedElementSelection(arrayOf("--include", "suite2.*")),
        listOf(
            Pair("suite1.test1", false),
            Pair("suite1.test2", false),
            Pair("suite2.test1", true),
            Pair("suite2.test2", true)
        )
    )

    @Test
    fun elementSelectionByEnvironment() = verifyElementSelection(
        EnvironmentBasedElementSelection(includePatterns = "suite1.*", excludePatterns = null),
        listOf(
            Pair("suite1.test1", true),
            Pair("suite1.test2", true),
            Pair("suite2.test1", false),
            Pair("suite2.test2", false)
        )
    )

    private fun verifyElementSelection(
        selection: TestElement.Selection,
        expectedResult: List<Pair<String, Boolean>>
    ): TestResult = withTestFramework {
        val suite1 by testSuite("suite1") {
            test("test1") {}
            test("test2") {}
        }

        val suite2 by testSuite("suite2") {
            test("test1") {}
            test("test2") {}
        }

        withTestReport(suite1, suite2, selection = selection) {
            with(finishedTestEvents()) {
                assertContentEquals(expectedResult, map { Pair(it.element.elementPath, it.element.isEnabled) })
            }
        }
    }
}
