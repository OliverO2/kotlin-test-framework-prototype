package fakeTestFramework

import testFramework.AbstractTestElement
import testFramework.TestElementPath

sealed class FakeTestElement(
    override val parentSuite: FakeTestSuite?,
    override val elementName: String = "[FakeTestElement]",
    override val displayName: String = elementName
) : AbstractTestElement {

    override val elementPath: TestElementPath
        get() = if (parentSuite != null) "${parentSuite?.elementPath}.$elementName" else elementName
}
