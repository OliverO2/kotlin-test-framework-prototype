package fakeTestFramework

import testFramework.AbstractTestSuite
import testFramework.TestDiscoverable
import testFramework.TestElementName

@TestDiscoverable
fun suite(@TestElementName name: String = "", content: FakeTestSuite.() -> Unit): Lazy<FakeTestSuite> = lazy {
    FakeTestSuite(
        elementName = name,
        content = content
    )
}

@TestDiscoverable
open class FakeTestSuite(@TestElementName elementName: String = "", content: FakeTestSuite.() -> Unit = {}) :
    FakeTestElement(null, elementName),
    AbstractTestSuite {

    init {
        content()
    }

    override val childElements: MutableList<FakeTestElement> = mutableListOf()

    override var isEnabled = true
}
