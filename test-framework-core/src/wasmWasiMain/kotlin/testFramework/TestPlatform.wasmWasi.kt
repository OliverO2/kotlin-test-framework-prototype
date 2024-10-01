package testFramework

actual val testPlatform = object : TestPlatform {
    override val displayName = "Wasm/WASI"
    override val parallelism = 1
    override fun threadId() = 0UL
    override fun threadDisplayName() = "single"
}
