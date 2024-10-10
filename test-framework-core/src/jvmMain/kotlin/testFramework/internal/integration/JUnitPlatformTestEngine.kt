package testFramework.internal.integration

import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import kotlinx.coroutines.runBlocking
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.Filter
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.ClassNameFilter
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.PackageNameFilter
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import testFramework.Test
import testFramework.TestElement
import testFramework.TestSession
import testFramework.TestSuite
import testFramework.annotations.TestSessionDeclaration
import testFramework.internal.EnvironmentBasedElementSelection
import testFramework.internal.TestEvent
import testFramework.internal.TestReport
import testFramework.internal.initializeTestFramework
import testFramework.internal.logDebug
import java.util.concurrent.ConcurrentHashMap

private var classLevelTestSuites = setOf<TestSuite>()
private val testElementDescriptors = ConcurrentHashMap<TestElement, AbstractTestDescriptor>()

internal class JUnitPlatformTestEngine : TestEngine {
    override fun getId(): String = "kotlin-test-framework-prototype"

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val classSelectors = discoveryRequest.getSelectorsByType(ClassSelector::class.java)
        val classNameFilter = Filter.composeFilters(discoveryRequest.getFiltersByType(ClassNameFilter::class.java))
        val packageNameFilter = Filter.composeFilters(discoveryRequest.getFiltersByType(PackageNameFilter::class.java))

        // Find suite classes via class selectors (ignoring all other selectors).
        val testSuiteClassesUninitialized = classSelectors
            .asSequence()
            .filter { classNameFilter.apply(it.className).included() }
            .map { Class.forName(it.className, false, it.classLoader) }
            .filter {
                packageNameFilter.apply(it.packageName).included() &&
                    // must be a suite
                    TestSuite::class.java.isAssignableFrom(it) &&
                    // but not the top-level session
                    !TestSession::class.java.isAssignableFrom(it)
            }
            .toList()

        // If no suites were found, return early, avoiding any initialization.
        if (testSuiteClassesUninitialized.isEmpty()) {
            // Do not initialize the test framework if no test classes have been discovered on the classpath.
            // This is probably a JVM-standalone invocation via the main() function for testing.
            return EngineDescriptor(UniqueId.forEngine(id), "${this::class.qualifiedName}")
        }

        // If a session class has been declared, instantiate it.
        val testSessionClassesUninitialized = withClassGraphAnnotationScan {
            getClassesWithAnnotation(TestSessionDeclaration::class.java)
                .map { Class.forName(it.name, false, this::class.java.classLoader) }
        }

        val testSession = when (testSessionClassesUninitialized.size) {
            0 -> null

            1 -> {
                testSessionClassesUninitialized.single().let {
                    Class.forName(it.name).getDeclaredConstructor().newInstance() as? TestSession
                        ?: throw IllegalArgumentException(
                            "Could not instantiate a ${TestSession::class.simpleName}" +
                                " from ${it.name} annotated with @${TestSessionDeclaration::class.simpleName}."
                        )
                } // instantiate
            }

            else -> throw IllegalArgumentException(
                "Found ${testSessionClassesUninitialized.size} classes" +
                    " annotated with @${TestSessionDeclaration::class.simpleName}," +
                    " but expected at most one." +
                    "\n\tAnnotated classes: $testSessionClassesUninitialized"
            )
        }

        // Initialize the framework first...
        initializeTestFramework(testSession)

        // ...then instantiate top-level suites,
        classLevelTestSuites = testSuiteClassesUninitialized
            .map { Class.forName(it.name).getDeclaredConstructor().newInstance() as TestSuite } // instantiate
            .toSet()

        // ...and configure the entire test element tree top-down, starting at the TestSession.
        TestSession.global.configure(
            EnvironmentBasedElementSelection(System.getenv("TEST_INCLUDE"), System.getenv("TEST_EXCLUDE"))
        )

        val engineDescriptor = EngineDescriptor(UniqueId.forEngine(id), "${this::class.qualifiedName}")
        log { "created EngineDescriptor(${engineDescriptor.uniqueId}, ${engineDescriptor.displayName})" }
        testElementDescriptors[TestSession.global] = engineDescriptor
        engineDescriptor.addChild(TestSession.global.newPlatformDescriptor(uniqueId))

        return engineDescriptor
    }

    override fun execute(request: ExecutionRequest) {
        if (classLevelTestSuites.isEmpty()) {
            // No tests were discovered. This is typically the case if the framework has been included
            // as a dependency, but another test framework is in charge. Make sure we don't report anything
            // to JUnit Platform's listener, otherwise JUnit Platform will complain.
            return
        }

        val listener = request.engineExecutionListener

        runBlocking {
            TestSession.global.execute(
                object : TestReport(FeedMode.ENABLED_ELEMENTS) {
                    override suspend fun add(event: TestEvent) {
                        when (event) {
                            is TestEvent.Starting -> {
                                log { "${event.element.platformDescriptor}: ${event.element} starting" }
                                listener.executionStarted(event.element.platformDescriptor)
                            }

                            is TestEvent.Finished -> {
                                log {
                                    "${event.element.platformDescriptor}: ${event.element} finished," +
                                        " result=${event.executionResult})"
                                }
                                listener.executionFinished(
                                    event.element.platformDescriptor,
                                    event.executionResult
                                )
                            }

                            is TestEvent.Skipped -> {
                                log { "${event.element.platformDescriptor}: ${event.element} skipped" }
                                listener.executionSkipped(event.element.platformDescriptor, "disabled")
                            }
                        }
                    }
                }
            )
        }
    }
}

private class TestElementJUnitPlatformDescriptor(
    uniqueId: UniqueId,
    displayName: String,
    source: TestSource?,
    val element: TestElement
) : AbstractTestDescriptor(uniqueId, displayName, source) {
    override fun getType(): TestDescriptor.Type = when (element) {
        is Test -> TestDescriptor.Type.TEST
        is TestSuite -> TestDescriptor.Type.CONTAINER
    }

    override fun toString(): String = "PD(uId=$uniqueId, dN=\"$displayName\", t=$type)"
}

private fun TestElement.newPlatformDescriptor(parentUniqueId: UniqueId): TestElementJUnitPlatformDescriptor {
    val source: TestSource?
    val uniqueId: UniqueId
    val element = this

    if (classLevelTestSuites.contains(this)) {
        uniqueId = parentUniqueId.append("class", element::class.qualifiedName!!)
        source = ClassSource.from(element::class.java)
    } else {
        val segmentType = when (element) {
            is Test -> "test"
            is TestSession -> "session"
            is TestSession.Compartment -> "compartment"
            is TestSuite -> "suite"
        }
        uniqueId = parentUniqueId.append(segmentType, simpleElementName)
        source = null
    }

    return TestElementJUnitPlatformDescriptor(
        uniqueId = uniqueId,
        displayName = simpleElementName,
        source = source,
        element = element
    ).apply {
        log { "created TestDescriptor($uniqueId, $displayName)" }
        testElementDescriptors[element] = this
        if (this@newPlatformDescriptor is TestSuite) {
            childElements.forEach { addChild(it.newPlatformDescriptor(uniqueId)) }
        }
    }
}

private val TestElement.platformDescriptor: AbstractTestDescriptor get() =
    checkNotNull(testElementDescriptors[this]) { "$this is missing its TestDescriptor" }

private val TestEvent.Finished.executionResult: TestExecutionResult get() =
    when (throwable) {
        null -> TestExecutionResult.successful()
        is AssertionError -> TestExecutionResult.failed(throwable)
        else -> TestExecutionResult.aborted(throwable)
    }

private fun <Result> withClassGraphAnnotationScan(action: ScanResult.() -> Result): Result = ClassGraph()
    .disableModuleScanning()
    .disableNestedJarScanning()
    .rejectPackages(
        "java.*",
        "javax.*",
        "sun.*",
        "com.sun.*",
        "kotlin.*",
        "kotlinx.*",
        "androidx.*",
        "org.jetbrains.kotlin.*",
        "org.junit.*"
    )
    .enableAnnotationInfo()
    .scan().use { scanResult ->
        scanResult.action()
    }

private fun log(message: () -> String) {
    logDebug(message)
}
