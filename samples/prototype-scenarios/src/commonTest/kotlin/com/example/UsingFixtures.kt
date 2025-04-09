package com.example

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import testFramework.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Use suspend-capable test fixtures across tests and suites.
// Fixtures lazily initialize on first use and automatically release resources when no longer needed.

val UsingFixtures by testSuite {
    val starRepository = fixture {
        StarRepository()
    } closeWith {
        disconnect()
    }

    testSuite("actual users") {
        val userRepository = fixture { UserRepository() }

        test("alina") {
            assertEquals(4, starRepository().userStars("alina"))
        }

        test("peter") {
            assertEquals(3, starRepository().userStars("peter"))
        }

        test("all") {
            userRepository().users().collect { user ->
                assertTrue(starRepository().userStars(user) > 0)
            }
        }
    }

    test("unknown") {
        assertEquals(0, starRepository().userStars("unknown"))
    }
}

private class UserRepository : AutoCloseable {
    suspend fun users(): Flow<String> = flowOf("alina", "peter")

    override fun close() {} // The standard (non-suspending) close function.
}

private class StarRepository {
    suspend fun userStars(user: String): Int = mapOf("alina" to 4, "peter" to 3)[user] ?: 0

    suspend fun disconnect() {} // Called via closeWith, so it can suspend.
}
