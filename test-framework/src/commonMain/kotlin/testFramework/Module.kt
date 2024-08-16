package testFramework

import testFramework.internal.forEachWithParallelism

class Module {
    private val specs: MutableList<Spec> = mutableListOf()

    internal val defaultGroup: SpecGroup = SpecGroup.default
    internal val defaultTestContainerConfiguration: TestContainerConfiguration = TestContainerConfiguration()

    fun configuration(action: SpecGroup.() -> Unit) {
        action(defaultGroup)
    }

    fun registerSpecs(vararg specs: Spec) {
        this.specs.addAll(specs)
    }

    suspend fun execute() {
        val specGroups = specs.groupBy { it.group }
        specGroups.forEach { (group, groupSpecs) ->
            println("\n––– Executing ${groupSpecs.size} spec(s) in $group –––")
            groupSpecs.forEachWithParallelism(group.specParallelism) { spec ->
                spec.execute()
            }
        }
    }

    companion object {
        val default: Module = Module()
    }
}
