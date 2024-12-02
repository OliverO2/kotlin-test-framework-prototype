package fakeTestFramework

import testFramework.AbstractTestElement
import testFramework.TestElementPath

sealed class TestElement(
    override val parentSuite: TestSuite?,
    override val elementName: String = "[TestElement]",
    override val displayName: String = elementName
) : AbstractTestElement {

    override val elementPath: TestElementPath
        get() = if (parentSuite != null) "${parentSuite?.elementPath}.$elementName" else elementName
}
