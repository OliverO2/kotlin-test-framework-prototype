package testFramework

actual val testPlatform: TestPlatform = TestPlatformJvm

object TestPlatformJvm : TestPlatform {
    override val displayName = "JVM"
    override val parallelism = Runtime.getRuntime().availableProcessors()
    override fun threadId() = Thread.currentThread().id.toULong()
    override fun threadDisplayName() = Thread.currentThread().name ?: "(thread ${threadId()})"
}
