package de.infix.testBalloon.integration.blockingDetection

import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.testScope

enum class BlockingDetection {
    DISABLED,
    ERROR,
    PRINT
}

/**
 * Returns a test configuration chaining [this] with a configuration enabling blocking call detection.
 *
 * Blocking call detection is only available on the JVM. This configuration has no effect on other platforms.
 *
 * NOTE: If blocking call detection is enabled, [TestConfig.testScope] will automatically be disabled. This is
 * necessary since kotlinx-coroutines uses blocking calls in `TestScope`.
 *
 * Child elements inherit this setting's effect.
 */
expect fun TestConfig.blockingDetection(mode: BlockingDetection = BlockingDetection.ERROR): TestConfig
