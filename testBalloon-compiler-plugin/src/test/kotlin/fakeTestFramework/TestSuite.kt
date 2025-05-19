package fakeTestFramework

import de.infix.testBalloon.framework.AbstractTestSuite
import de.infix.testBalloon.framework.TestDiscoverable
import de.infix.testBalloon.framework.TestElementName

@Suppress("unused")
@TestDiscoverable
fun testSuite(@TestElementName name: String = "", content: TestSuite.() -> Unit): Lazy<TestSuite> = lazy {
    TestSuite(
        name = name,
        content = content
    )
}

@TestDiscoverable
open class TestSuite(@TestElementName name: String = "", content: TestSuite.() -> Unit = {}) :
    TestElement(null, name),
    AbstractTestSuite {

    init {
        content()
    }

    override val testElementChildren: MutableList<TestElement> = mutableListOf()

    override var testElementIsEnabled = true
}
