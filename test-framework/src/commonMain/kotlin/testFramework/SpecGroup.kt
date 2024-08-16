package testFramework

import testFramework.internal.platformParallelism

class SpecGroup internal constructor(
    val name: String,
    var specParallelism: Int = 1,
    internal val testContainerConfiguration: TestContainerConfiguration? = null
) {
    fun configuration(action: SpecGroup.() -> Unit) {
        action(this)
    }

    override fun toString(): String = "group:$name"

    companion object {
        val parallel: SpecGroup = SpecGroup("parallel($platformParallelism)", specParallelism = platformParallelism)
        val singleThreaded: SpecGroup = SpecGroup("single-threaded", specParallelism = 1)
        val default: SpecGroup = parallel
    }
}
