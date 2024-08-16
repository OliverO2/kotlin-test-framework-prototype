package testFramework

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

typealias TestAction = suspend Test.(invocation: TestInvocation) -> Unit
typealias TestConfigurationAction = TestConfiguration.() -> Unit

class Test(
    private val parent: TestContainer,
    private val simpleName: String,
    configuration: TestConfigurationAction?,
    private val testAction: TestAction
) : TestContainer(parent.testContainerConfiguration) {
    override val testScopeName: String = "${parent.testScopeName}.$simpleName"
    val testName: String get() = testScopeName

    internal val testConfiguration: TestConfiguration = TestConfiguration().apply { configuration?.invoke(this) }

    internal val isEnabled get() = testConfiguration.isEnabled && !simpleName.startsWith('!')
    internal val isFocused get() = simpleName.startsWith("f:")

    internal suspend fun execute() {
        val timeout = testConfiguration.timeout

        val aroundEachTestAction = parent.testContainerConfiguration.aroundEachTestAction

        suspend fun wrappedTestAction(testInvocation: TestInvocation) {
            if (aroundEachTestAction == null) {
                testAction(testInvocation)
                executeComponents()
            } else {
                aroundEachTestAction(this@Test, testInvocation) {
                    testAction(testInvocation)
                    executeComponents()
                }
            }
        }

        for (invocationIndex in 0..<testConfiguration.invocationCount) {
            val testInvocation = TestInvocation(this@Test, invocationIndex)

            parent.testContainerConfiguration.beforeEachTestAction?.invoke(parent, testInvocation)

            if (timeout != null) {
                try {
                    withTimeout(timeout) {
                        wrappedTestAction(testInvocation)
                    }
                } catch (exception: TimeoutCancellationException) {
                    throw AssertionError("$exception")
                }
            } else {
                wrappedTestAction(testInvocation)
            }

            parent.testContainerConfiguration.afterEachTestAction?.invoke(parent, testInvocation)
        }
    }
}
