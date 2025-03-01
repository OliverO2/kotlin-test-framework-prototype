package testFramework

actual val testPlatform: TestPlatform = TestPlatformWasmWasi

object TestPlatformWasmWasi : TestPlatform {
    override val displayName = "Wasm/WASI"
    override val parallelism = 1
    override fun threadId() = 0UL
    override fun threadDisplayName() = "single"
}
