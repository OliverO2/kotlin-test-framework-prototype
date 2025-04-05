package com.example

import io.kotest.assertions.errorCollectorContextElement
import testFramework.TestConfig
import testFramework.coroutineContext

actual fun TestConfig.withKotestAssertions(): TestConfig = coroutineContext(errorCollectorContextElement)
