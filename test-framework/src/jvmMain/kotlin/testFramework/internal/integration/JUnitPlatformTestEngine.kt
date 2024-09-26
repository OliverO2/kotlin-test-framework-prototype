package testFramework.internal.integration

import kotlinx.coroutines.runBlocking
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import testFramework.Test
import testFramework.TestScope
import testFramework.TestSuite
import testFramework.internal.TestSession
import java.util.concurrent.ConcurrentHashMap

private lateinit var classLevelTestSuites: Set<TestSuite<*>>
private val scopeDescriptors = ConcurrentHashMap<TestScope, AbstractTestDescriptor>()

internal class JUnitPlatformTestEngine : TestEngine {
    override fun getId(): String = "kotlin-test-framework-prototype"

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val classSelectors = discoveryRequest.getSelectorsByType(ClassSelector::class.java)

        // Instantiate all TestSuite classes requested via class selectors
        classLevelTestSuites = classSelectors
            .asSequence()
            .map { Class.forName(it.className, false, it.classLoader) } // classes, but not initialized yet
            .filter { TestSuite::class.java.isAssignableFrom(it) } // only TestSuite classes from here
            .map { Class.forName(it.name).getDeclaredConstructor().newInstance() as TestSuite<*> } // instantiate
            .toSet()

        // TODO: Check how IntelliJ IDEA runs failed tests
        // - Check which selectors are included in discoveryRequest in this case.
        // - Check how to deal with getSelectorsByType(UniqueIdSelector::class.java)
        //   see kotest-runner/kotest-runner-junit5/src/jvmMain/kotlin/io/kotest/runner/junit/platform/discoveryRequest.kt

        TestSession.configure()

        return EngineDescriptor(
            UniqueId.forEngine(id),
            "${this::class.qualifiedName}"
        ).apply {
            log("created EngineDescriptor(${this.uniqueId}, $displayName)")
            scopeDescriptors[TestSession] = this
            addChild(TestSession.newPlatformDescriptor(uniqueId))
        }
    }

    override fun execute(request: ExecutionRequest) {
        val listener = request.engineExecutionListener

        runBlocking {
            TestSession.execute { event: TestScope.Event ->
                when (event) {
                    is TestScope.Event.Starting -> {
                        log("${event.scope.platformDescriptor}: ${event.scope} starting")
                        listener.executionStarted(event.scope.platformDescriptor)
                    }

                    is TestScope.Event.Finished -> {
                        log(
                            "${event.scope.platformDescriptor}: ${event.scope} finished," +
                                " result=${event.executionResult})"
                        )
                        listener.executionFinished(event.scope.platformDescriptor, event.executionResult)
                    }

                    is TestScope.Event.Skipped -> {
                        log("${event.scope.platformDescriptor}: ${event.scope} skipped")
                        listener.executionSkipped(event.scope.platformDescriptor, "disabled")
                    }
                }
            }
        }
    }
}

private class TestScopeJUnitPlatformDescriptor(
    uniqueId: UniqueId,
    displayName: String,
    source: TestSource?,
    val scope: TestScope
) : AbstractTestDescriptor(uniqueId, displayName, source) {
    override fun getType(): TestDescriptor.Type = when (scope) {
        is Test<*> -> TestDescriptor.Type.TEST
        is TestSuite<*> -> TestDescriptor.Type.CONTAINER
    }

    override fun toString(): String = "PD(uId=$uniqueId, dN=\"$displayName\", t=$type)"
}

private fun TestScope.newPlatformDescriptor(parentUniqueId: UniqueId): TestScopeJUnitPlatformDescriptor {
    val source: TestSource?
    val uniqueId: UniqueId
    val scope = this

    if (classLevelTestSuites.contains(this)) {
        uniqueId = parentUniqueId.append("class", scope::class.qualifiedName!!)
        source = ClassSource.from(scope::class.java)
    } else {
        val segmentType = when (scope) {
            is Test<*> -> "test"
            is TestSession -> "session"
            is TestSuite<*> -> "suite"
        }
        uniqueId = parentUniqueId.append(segmentType, simpleScopeName)
        source = null
    }

    return TestScopeJUnitPlatformDescriptor(
        uniqueId = uniqueId,
        displayName = simpleScopeName,
        source = source,
        scope = scope
    ).apply {
        log("created TestDescriptor($uniqueId, $displayName)")
        scopeDescriptors[scope] = this
        if (this@newPlatformDescriptor is TestSuite<*>) {
            childScopes.forEach { addChild(it.newPlatformDescriptor(uniqueId)) }
        }
    }
}

private val TestScope.platformDescriptor: AbstractTestDescriptor get() =
    checkNotNull(scopeDescriptors[this]) { "Scope $this is missing its TestDescriptor" }

private val TestScope.Event.Finished.executionResult: TestExecutionResult get() =
    when (throwable) {
        null -> TestExecutionResult.successful()
        is AssertionError -> TestExecutionResult.failed(throwable)
        else -> TestExecutionResult.aborted(throwable)
    }

private fun log(message: String) {
    // println("JUPTE: $message")
}
