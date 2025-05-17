package de.infix.testBalloon.integration.kotest.assertions

import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.coroutineContext
import io.kotest.assertions.errorCollectorContextElement

actual fun TestConfig.kotestAssertionsSupport(): TestConfig = coroutineContext(errorCollectorContextElement)
