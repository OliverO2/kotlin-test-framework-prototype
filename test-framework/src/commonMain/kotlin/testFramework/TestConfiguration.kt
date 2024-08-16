package testFramework

import kotlin.time.Duration

data class TestConfiguration(var isEnabled: Boolean = true, var invocationCount: Int = 1, var timeout: Duration? = null)
