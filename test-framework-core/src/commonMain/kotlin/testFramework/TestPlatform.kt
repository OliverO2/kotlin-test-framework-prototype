package testFramework

interface TestPlatform {
    val displayName: String
    val parallelism: Int
    fun threadId(): ULong
    fun threadDisplayName(): String
}

expect val testPlatform: TestPlatform
