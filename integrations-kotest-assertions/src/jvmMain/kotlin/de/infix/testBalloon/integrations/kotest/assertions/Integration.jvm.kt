package de.infix.testBalloon.integrations.kotest.assertions

import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.coroutineContext
import io.kotest.assertions.errorCollectorContextElement

actual fun TestConfig.withKotestAssertions(): TestConfig = coroutineContext(errorCollectorContextElement)
