package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.withContext
import testFramework.testSuite
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// kotlinx.coroutines `TestScope` is available by default in each test.
// - Tests run on virtual time by default (like `runTest` does).
// - The `TestScope` is available via the `testScope` property.
//
// This behavior can be disabled via `TestConfig.testScope(isEnabled = false)`.

@OptIn(ExperimentalCoroutinesApi::class)
val UsingTestScope by testSuite {
    test("virtual time") {
        assertEquals(0L, testScope.currentTime)
        delay(10.milliseconds)
        assertEquals(10L, testScope.currentTime)
    }

    test("background job") {
        testScope.backgroundScope.launch {
            withContext(Dispatchers.Default) {
                delay(100.seconds)
            }
        }
    }
}
