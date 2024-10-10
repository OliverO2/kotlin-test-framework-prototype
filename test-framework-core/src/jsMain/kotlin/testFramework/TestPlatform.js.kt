package testFramework

// https://stackoverflow.com/a/31090240
internal val isNodeJs: Boolean =
    js("(new Function('try { return this === global; } catch(e) { return false; }'))()") as Boolean

actual val testPlatform = object : TestPlatform {
    override val displayName = if (isNodeJs) "JS/Node" else "JS/Browser"
    override val parallelism = 1
    override fun threadId() = 0UL
    override fun threadDisplayName() = "single"
}
