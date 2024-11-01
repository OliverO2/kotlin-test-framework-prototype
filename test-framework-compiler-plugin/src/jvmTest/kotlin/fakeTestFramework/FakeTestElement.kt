package fakeTestFramework

import testFramework.AbstractTestElement
import testFramework.TestElementPath

sealed class FakeTestElement(override val parentSuite: FakeTestSuite?, simpleNameOrNull: String?) :
    AbstractTestElement {
    override val displayName: String by lazy {
        simpleNameOrNull ?: this::class.simpleName ?: "[FakeTestElement]"
    }

    override val elementPath: TestElementPath
        get() =
            if (parentSuite != null) "${parentSuite?.elementPath}.$displayName" else displayName
}
