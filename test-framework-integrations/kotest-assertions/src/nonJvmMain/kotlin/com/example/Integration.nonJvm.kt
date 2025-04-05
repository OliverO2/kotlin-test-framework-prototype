package com.example

import testFramework.TestConfig

actual fun TestConfig.withKotestAssertions(): TestConfig = TestConfig
// NOTE: This must change once Kotest fully supports an ErrorCollector on multithreaded platforms other than the JVM.
