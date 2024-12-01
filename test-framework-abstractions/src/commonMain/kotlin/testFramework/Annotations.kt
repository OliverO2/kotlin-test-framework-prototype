package testFramework

/**
 * Makes a class or function support automatic discovery of test classes or properties.
 *
 * Top-level test suites are declared
 * - via top-level properties lazily initialized by a `@[TestDiscoverable]` function,
 * - as top-level subclasses of a `@[TestDiscoverable]` class implementing [AbstractTestSuite].
 *
 * A test session can be declared as a top-level subclass of a `@[TestDiscoverable]` class
 * implementing [AbstractTestSession].
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class TestDiscoverable

/**
 * Makes a String parameter receive the fully qualified name of its function's or constructor's caller.
 *
 * This annotation requires a top-level `@[TestDiscoverable]` function or class constructor. A `@[TestElementName]`
 * parameter must be of type String and have a default value (typically, "").
 *
 * If the call site of the function or constructor does not supply an actual value for that `@[TestElementName]`
 * parameter, the compiler will insert the caller's fully qualified class or property name.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class TestElementName

/**
 * Makes a String parameter receive the simple name of its function's or constructor's caller.
 *
 * For prerequisites and mechanism, see [TestElementName].
 *
 * The display name generated is the simple name of the caller's class or property.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class TestDisplayName

@DslMarker
annotation class TestElementDsl
