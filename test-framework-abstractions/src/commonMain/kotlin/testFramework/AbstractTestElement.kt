package testFramework

@TestElementDsl
interface AbstractTestElement {
    val parentSuite: AbstractTestSuite?
    val elementPath: TestElementPath
    val displayName: String
    val isEnabled: Boolean
}

interface AbstractTest : AbstractTestElement

interface AbstractTestSuite : AbstractTestElement {
    val childElements: Iterable<AbstractTestElement>
}

interface AbstractTestSession : AbstractTestSuite

typealias TestElementPath = String
