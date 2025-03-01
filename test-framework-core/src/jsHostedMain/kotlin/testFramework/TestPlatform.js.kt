package testFramework

interface TestPlatformJsHosted : TestPlatform {
    enum class Runtime(val displayName: String) {
        NODE("Node"),
        BROWSER("Browser");

        override fun toString(): String = displayName
    }

    val runtime: Runtime

    override val parallelism get() = 1
    override fun threadId() = 0UL
    override fun threadDisplayName() = "single"
}
