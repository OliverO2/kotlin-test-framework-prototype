package testFramework

// https://stackoverflow.com/a/31090240
private val isNodeJs: Boolean = js("(new Function('try {return this===global;}catch(e){return false;}'))()")

actual val testPlatform = object : TestPlatform {
    override val displayName = if (isNodeJs) "Wasm/JS/Node" else "Wasm/JS/Browser"
    override val parallelism = 1
    override fun threadId() = 0UL
    override fun threadDisplayName() = "single"
}
