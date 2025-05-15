package com.example

import de.infix.testBalloon.framework.testSuite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// Use suspend-capable test fixtures across tests and suites.
// Fixtures lazily initialize on first use and automatically release resources when no longer needed.

val UsingFixtures by testSuite {
    val starRepository = testFixture {
        StarRepository()
    } closeWith {
        disconnect()
    }

    testSuite("actual users") {
        val userRepository = testFixture { UserRepository(testSuiteScope) }

        test("alina") {
            assertEquals(4, starRepository().userStars("alina"))
        }

        test("peter") {
            assertEquals(3, starRepository().userStars("peter"))
        }

        test("stars for all") {
            userRepository().users().collect { user ->
                assertTrue(starRepository().userStars(user) > 0)
            }
        }
    }

    test("unknown") {
        assertEquals(0, starRepository().userStars("unknown"))
    }
}

private class UserRepository(scope: CoroutineScope) : AutoCloseable {
    val clientJob = scope.launch {
        // Could be running infinitely like with
        //     client.webSocket("https://ktor.io/docs/client-websockets.html") { ... }
        delay(3.seconds)
    }

    suspend fun users(): Flow<String> = flowOf("alina", "peter")

    override fun close() { // The standard (non-suspending) close function.
        clientJob.cancel()
    }
}

private class StarRepository {
    suspend fun userStars(user: String): Int = mapOf("alina" to 4, "peter" to 3)[user] ?: 0

    suspend fun disconnect() {} // Called via closeWith, so it can suspend.
}
