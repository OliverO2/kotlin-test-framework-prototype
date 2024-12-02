package fakeTestFramework

import testFramework.AbstractTestSuite
import testFramework.TestDiscoverable
import testFramework.TestElementName

@TestDiscoverable
fun testSuite(@TestElementName name: String = "", content: TestSuite.() -> Unit): Lazy<TestSuite> = lazy {
    TestSuite(
        elementName = name,
        content = content
    )
}

@TestDiscoverable
open class TestSuite(@TestElementName elementName: String = "", content: TestSuite.() -> Unit = {}) :
    TestElement(null, elementName),
    AbstractTestSuite {

    init {
        content()
    }

    override val childElements: MutableList<TestElement> = mutableListOf()

    override var isEnabled = true
}
