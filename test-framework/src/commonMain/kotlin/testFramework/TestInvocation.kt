package testFramework

class TestInvocation(val test: Test, val invocationIndex: Int) {
    override fun toString(): String = buildString {
        append(test.testName)
        listOfNotNull(
            test.testConfiguration.invocationCount.let {
                if (it > 1) "#${invocationIndex + 1}/${test.testConfiguration.invocationCount}" else null
            },
            test.testConfiguration.timeout?.let { "timeout=$it" }
        ).let {
            if (it.isNotEmpty()) {
                append(it.joinToString(prefix = "(", postfix = ")"))
            }
        }
    }
}
