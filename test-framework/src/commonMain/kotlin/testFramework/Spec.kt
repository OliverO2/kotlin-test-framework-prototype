package testFramework

open class Spec(
    private val module: Module,
    val group: SpecGroup = module.defaultGroup,
    private val configurationAction: TestContainerAction
) : TestContainer(group.testContainerConfiguration ?: module.defaultTestContainerConfiguration, configurationAction) {
    override val testScopeName: String get() = "${this::class.simpleName}"

    constructor(group: SpecGroup, configurationAction: TestContainerAction) : this(
        Module.default,
        group,
        configurationAction
    )

    constructor(configurationAction: TestContainerAction) : this(
        Module.default,
        configurationAction = configurationAction
    )

    suspend fun execute() {
        executeComponents()
    }
}
