package de.infix.testBalloon.integrations.kotest.assertions

import de.infix.testBalloon.framework.TestConfig

actual fun TestConfig.withKotestAssertions(): TestConfig = TestConfig
// NOTE: This must change once Kotest fully supports an ErrorCollector on multithreaded platforms other than the JVM.
