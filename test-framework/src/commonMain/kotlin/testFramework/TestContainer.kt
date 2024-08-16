package testFramework

import testFramework.internal.forEachWithParallelism

abstract class TestContainer internal constructor(
    testContainerConfiguration: TestContainerConfiguration,
    private val configurationAction: TestContainerAction? = null
) {
    internal val testContainerConfiguration: TestContainerConfiguration = testContainerConfiguration.copy()
    abstract val testScopeName: String

    private val testComponents: MutableList<Test> = mutableListOf()

    fun beforeFirstTest(action: TestContainerAction) {
        testContainerConfiguration.beforeFirstTestAction = action
    }

    fun beforeEachTest(action: TestContainerTestInvocationAction) {
        testContainerConfiguration.beforeEachTestAction = action
    }

    fun aroundEachTest(action: TestWrappingAction) {
        testContainerConfiguration.aroundEachTestAction = action
    }

    fun afterEachTest(action: TestContainerTestInvocationAction) {
        testContainerConfiguration.afterEachTestAction = action
    }

    fun afterLastTest(action: TestContainerAction) {
        testContainerConfiguration.afterLastTestAction = action
    }

    fun test(name: String, configuration: TestConfigurationAction? = null, action: TestAction) {
        testComponents.add(Test(this, name, configuration, action))
    }

    protected suspend fun executeComponents() {
        configurationAction?.invoke(this)

        val focusedTests = testComponents.filter { it.isFocused }
        val candidateTests = focusedTests.ifEmpty { testComponents }.filter { it.isEnabled }

        if (candidateTests.isEmpty()) return

        testContainerConfiguration.beforeFirstTestAction?.invoke(this)

        candidateTests.forEachWithParallelism(testContainerConfiguration.testParallelism) { test ->
            test.execute()
        }

        testContainerConfiguration.afterLastTestAction?.invoke(this)
    }
}
