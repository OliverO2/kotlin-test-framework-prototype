package fakeTestFramework

import testFramework.AbstractTestSuite
import testFramework.TestDiscoverable
import testFramework.TestName

@TestDiscoverable
fun suite(@TestName name: String, content: FakeTestSuite.() -> Unit): Lazy<FakeTestSuite> = lazy {
    FakeTestSuite(
        simpleNameOrNull = name,
        content = content
    )
}

@TestDiscoverable
open class FakeTestSuite(@TestName simpleNameOrNull: String? = null, content: FakeTestSuite.() -> Unit = {}) :
    FakeTestElement(null, simpleNameOrNull),
    AbstractTestSuite {

    init {
        content()
    }

    constructor(content: FakeTestSuite.() -> Unit = {}) : this(null, content)

    override val childElements: MutableList<FakeTestElement> = mutableListOf()

    override var isEnabled = true
}
