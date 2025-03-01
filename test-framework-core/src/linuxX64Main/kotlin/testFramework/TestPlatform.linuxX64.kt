package testFramework

import platform.posix._SC_NPROCESSORS_ONLN
import platform.posix.pthread_self
import platform.posix.sysconf

actual val testPlatform: TestPlatform = TestPlatformLinuxX64

object TestPlatformLinuxX64 : TestPlatform {
    override val displayName = "Linux/X64"
    override val parallelism = sysconf(_SC_NPROCESSORS_ONLN).toInt()
    override fun threadId() = pthread_self()
    override fun threadDisplayName() = threadId().toString()
}
