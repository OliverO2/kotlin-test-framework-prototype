package testFramework

/**
 * An element of a test hierarchy. Can be a test or a suite.
 */
@TestElementDsl
interface AbstractTestElement {
    val parentSuite: AbstractTestSuite?

    /**
     * A path uniquely identifying the element in its test hierarchy.
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

/**
 * A test containing test logic which raises assertion errors on failure.
 */
interface AbstractTest : AbstractTestElement

/**
 * A test suite declaring a number of child test elements (tests and/or suites).  A suite may not contain test logic.
 */
interface AbstractTestSuite : AbstractTestElement {
    val childElements: Iterable<AbstractTestElement>
}

/**
 * A compilation module's root test suite, typically holding the module-wide default configuration.
 *
 * A compilation module may declare at most one test session. It is the root of the test element hierarchy.
 */
interface AbstractTestSession : AbstractTestSuite

/**
 * A path uniquely identifying a test element in its test hierarchy.
 */
typealias TestElementPath = String
