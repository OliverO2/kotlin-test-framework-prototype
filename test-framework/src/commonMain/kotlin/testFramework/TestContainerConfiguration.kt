package testFramework

typealias TestContainerAction = suspend TestContainer.() -> Unit
typealias TestContainerTestInvocationAction = suspend TestContainer.(invocation: TestInvocation) -> Unit
typealias TestWrappingAction = suspend (test: Test, invocation: TestInvocation, testAction: TestAction) -> Unit

internal data class TestContainerConfiguration(
    var beforeFirstTestAction: TestContainerAction? = null,
    var beforeEachTestAction: TestContainerTestInvocationAction? = null,
    var aroundEachTestAction: TestWrappingAction? = null,
    var afterEachTestAction: TestContainerTestInvocationAction? = null,
    var afterLastTestAction: TestContainerAction? = null,
    var testParallelism: Int = 1
)
