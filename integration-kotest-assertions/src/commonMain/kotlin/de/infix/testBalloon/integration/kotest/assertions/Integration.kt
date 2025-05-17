package de.infix.testBalloon.integration.kotest.assertions

import de.infix.testBalloon.framework.TestConfig

/**
 * Returns a test configuration chaining [this] with a configuration supporting Kotest assertions.
 *
 * Some Kotest's assertion library functions like `assertSoftly` and `withClue` require a special setup, which
 * this configuration provides.
 *
 * Child elements inherit this setting's effect.
 */
expect fun TestConfig.kotestAssertionsSupport(): TestConfig
