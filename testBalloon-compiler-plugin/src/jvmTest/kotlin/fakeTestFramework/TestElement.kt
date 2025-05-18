package fakeTestFramework

import de.infix.testBalloon.framework.AbstractTestElement
import de.infix.testBalloon.framework.TestElementPath

sealed class TestElement(
    override val testElementParent: TestSuite?,
    override val testElementName: String = "[TestElement]",
    override val testElementDisplayName: String = testElementName
) : AbstractTestElement {

    override val testElementPath: TestElementPath
        get() = if (testElementParent !=
            null
        ) {
            "${testElementParent?.testElementPath}.$testElementName"
        } else {
            testElementName
        }
}
