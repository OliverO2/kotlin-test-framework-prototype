package testFramework.internal

import testFramework.AbstractTestSuite
import testFramework.TestElement
import testFramework.TestFrameworkInvokedByGeneratedCode
import testFramework.TestSession
import testFramework.internal.integration.IntellijLogTestReport
import testFramework.internal.integration.kotlinJsTestFrameworkAvailable
import testFramework.internal.integration.processArguments
import testFramework.internal.integration.registerWithKotlinJsTestFramework

@TestFrameworkInvokedByGeneratedCode
internal actual suspend fun configureAndExecuteTests(suites: Array<AbstractTestSuite>) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    configureTestsCatching {
        TestSession.global.configure(
            argumentsBasedElementSelection
                ?: processArguments()?.let { ArgumentsBasedElementSelection(it) }
                ?: TestElement.AllInSelection
        )
    }.onSuccess {
        executeTestsCatching {
            if (kotlinJsTestFrameworkAvailable()) {
                TestSession.global.registerWithKotlinJsTestFramework()
            } else {
                TestSession.global.execute(IntellijLogTestReport())
            }
        }
    }
}
