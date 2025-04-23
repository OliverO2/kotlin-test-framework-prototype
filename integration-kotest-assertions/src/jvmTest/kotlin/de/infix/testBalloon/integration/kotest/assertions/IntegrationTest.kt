package de.infix.testBalloon.integration.kotest.assertions

import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.testSuite
import io.kotest.assertions.MultiAssertionError
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotEndWith

val IntegrationTest by testSuite {
    test("assertSoftly", configuration = TestConfig.supportKotestAssertions()) {
        shouldThrow<MultiAssertionError> {
            assertSoftly {
                "Expect failure 1!" shouldNotEndWith "!"
                "Expect failure 2!" shouldNotEndWith "!"
            }
        }.message shouldContain "The following 2 assertions failed:"
    }
}
