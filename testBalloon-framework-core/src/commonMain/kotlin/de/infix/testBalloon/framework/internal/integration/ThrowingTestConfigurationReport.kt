package de.infix.testBalloon.framework.internal.integration

import de.infix.testBalloon.framework.TestConfigurationReport
import de.infix.testBalloon.framework.TestElementEvent

/**
 * A [TestConfigurationReport] which relays configuration errors to the standard exception handling mechanism.
 */
internal class ThrowingTestConfigurationReport : TestConfigurationReport() {
    override fun add(event: TestElementEvent) {
        if (event is TestElementEvent.Finished && event.throwable != null) {
            if (event.throwable is TestConfigurationError) {
                throw event.throwable
            } else {
                throw TestConfigurationError("Could not configure ${event.element}", event.throwable)
            }
        }
    }
}

internal class TestConfigurationError(message: String, cause: Throwable) : Error(message, cause)
