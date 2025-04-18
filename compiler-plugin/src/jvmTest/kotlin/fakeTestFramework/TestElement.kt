package fakeTestFramework

import de.infix.testBalloon.framework.AbstractTestElement
import de.infix.testBalloon.framework.TestElementPath

sealed class TestElement(
    override val parentSuite: TestSuite?,
    override val elementName: String = "[TestElement]",
    override val displayName: String = elementName
) : AbstractTestElement {

    override val elementPath: TestElementPath
        get() = if (parentSuite != null) "${parentSuite?.elementPath}.$elementName" else elementName
}
