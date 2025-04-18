package de.infix.testBalloon.framework.internal

import de.infix.testBalloon.framework.AbstractTestSuite
import de.infix.testBalloon.framework.InvokedByGeneratedCode
import de.infix.testBalloon.framework.TestElement
import de.infix.testBalloon.framework.TestSession
import de.infix.testBalloon.framework.internal.integration.IntellijLogTestReport
import de.infix.testBalloon.framework.internal.integration.kotlinJsTestFrameworkAvailable
import de.infix.testBalloon.framework.internal.integration.processArguments
import de.infix.testBalloon.framework.internal.integration.registerWithKotlinJsTestFramework

@InvokedByGeneratedCode
internal actual suspend fun configureAndExecuteTests(suites: Array<AbstractTestSuite>) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    configureTestsCatching {
        TestSession.global.parameterize(
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
