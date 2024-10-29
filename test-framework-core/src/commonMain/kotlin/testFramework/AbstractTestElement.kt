package testFramework

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME) // TODO: BINARY if JVM discovery uses the compiler plugin
annotation class TestDiscoverable

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class TestName

@DslMarker
annotation class TestElementDsl

@TestElementDsl
sealed interface AbstractTestElement {
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
