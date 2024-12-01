package testFramework

/**
 * An element of a test tree. Can be a test or a suite.
 */
@TestElementDsl
interface AbstractTestElement {
    val parentSuite: AbstractTestSuite?

    /**
     * A path uniquely identifying the element in its test tree.
     *
     * The element's path must be identical across multiple test sessions.
     * Element paths must form a hierarchy: Each element's path must be prefixed with that of its parent element.
     */
    val elementPath: TestElementPath

    /**
     * The element's name, uniquely identifying it within its suite or the top level.
     */
    val elementName: String

    /**
     * A shortened variant of the element's name.
     */
    val displayName: String

    val isEnabled: Boolean
}

interface AbstractTest : AbstractTestElement

interface AbstractTestSuite : AbstractTestElement {
    val childElements: Iterable<AbstractTestElement>
}

interface AbstractTestSession : AbstractTestSuite

typealias TestElementPath = String
