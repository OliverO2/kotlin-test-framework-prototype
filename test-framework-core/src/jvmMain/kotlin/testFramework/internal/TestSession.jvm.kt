package testFramework.internal

import testFramework.TestElement
import testFramework.TestSession
import testFramework.internal.integration.IntellijTestLog

actual suspend fun runTests(vararg suites: Any) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    // On the JVM, tests will be discovered and run via JUnit Platform.
    // In this case, this function is not used.
    // It is intended for internal framework testing only.

    configureTestsCatching {
        TestSession.global.configure(argumentsBasedElementSelection ?: TestElement.AllInSelection)
    }.onSuccess {
        executeTestsCatching {
            TestSession.global.execute(IntellijTestLog)
        } 
    }
}
