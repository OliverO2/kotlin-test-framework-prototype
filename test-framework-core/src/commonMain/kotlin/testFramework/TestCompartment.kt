package testFramework

open class TestCompartment(name: String, configuration: TestElementConfiguration.() -> Unit) :
    TestSuite(parent = TestSession.global, simpleNameOrNull = "@$name", configuration = configuration) {

    companion object {
        val Sequential by lazy {
            TestCompartment(name = "Sequential", configuration = { isSequential = true })
        }

        val Parallel by lazy {
            TestCompartment(name = "Parallel", configuration = { parallelism = testPlatform.parallelism })
        }
    }
}
