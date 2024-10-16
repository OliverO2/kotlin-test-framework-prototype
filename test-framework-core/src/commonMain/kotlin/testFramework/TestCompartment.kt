package testFramework

open class TestCompartment(name: String, configuration: TestElementConfiguration.() -> Unit) :
    TestSuite(parentSuite = TestSession.global, simpleNameOrNull = "@$name", configuration = configuration) {

    companion object {
        val Default by lazy {
            TestCompartment(name = "Default", configuration = {
                suiteConcurrency = Concurrency.Sequential
                testConcurrency = Concurrency.TestScoped()
            })
        }

        val Parallel by lazy {
            TestCompartment(name = "Parallel", configuration = {
                suiteConcurrency = Concurrency.Parallel()
                testConcurrency = Concurrency.Inherited
            })
        }
    }
}
