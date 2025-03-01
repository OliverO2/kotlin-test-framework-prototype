package testFramework

actual val testPlatform: TestPlatform = TestPlatformWasmJs

object TestPlatformWasmJs : TestPlatformJsHosted {
    override val runtime: TestPlatformJsHosted.Runtime =
        if (runtimeIsNodeJs()) TestPlatformJsHosted.Runtime.NODE else TestPlatformJsHosted.Runtime.BROWSER

    override val displayName = "Wasm/JS/$runtime"
}

// https://stackoverflow.com/a/31090240
private fun runtimeIsNodeJs(): Boolean =
    js("(new Function('try { return this === global; } catch(e) { return false; }'))()")
