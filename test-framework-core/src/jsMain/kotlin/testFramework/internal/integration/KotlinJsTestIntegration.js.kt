package testFramework.internal.integration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import testFramework.TestPlatformJs
import testFramework.TestPlatformJsHosted

internal actual fun kotlinJsTestFrameworkAvailable(): Boolean =
    js("typeof describe === 'function' && typeof it === 'function'") as Boolean

internal actual val kotlinJsTestFramework: KotlinJsTestFramework = object : KotlinJsTestFramework {
    override fun suite(name: String, ignored: Boolean, suiteFn: () -> Unit) {
        frameworkAdapter.suite(name, ignored, suiteFn)
    }

    override fun test(name: String, ignored: Boolean, testFn: () -> JsPromiseLike?) {
        frameworkAdapter.test(name, ignored, testFn)
    }
}

internal actual fun CoroutineScope.testFunctionPromise(testFunction: suspend () -> Unit): JsPromiseLike? =
    promise { testFunction() }

internal actual fun processArguments(): Array<String>? =
    if (TestPlatformJs.runtime == TestPlatformJsHosted.Runtime.NODE) js("process.argv") as Array<String> else null

/**
 * JS test framework adapter interface defined by the Kotlin/JS test infra.
 *
 * This interface allows framework function invocations to be conditionally transformed as required for proper
 * reporting of [failing JS tests on Node.js](https://youtrack.jetbrains.com/issue/KT-64533).
 *
 * Inside the Kotlin/JS test infra, the interface is actually known as `KotlinTestRunner`:
 *     https://github.com/JetBrains/kotlin/blob/v1.9.23/libraries/tools/kotlin-test-js-runner/src/KotlinTestRunner.ts
 * Proper test reporting depends on using kotlinTest.adapterTransformer, which is defined here for Node.js:
 *     https://github.com/JetBrains/kotlin/blob/v1.9.23/libraries/tools/kotlin-test-js-runner/nodejs.ts
 */
private external interface FrameworkAdapter {
    /** Declares a test suite. */
    fun suite(name: String, ignored: Boolean, suiteFn: () -> Unit)

    /** Declares a test. */
    fun test(name: String, ignored: Boolean, testFn: () -> JsPromiseLike?)
}

// Conditional transformation required by the Kotlin/JS test infra.
private val frameworkAdapter: FrameworkAdapter by lazy {
    val originalAdapter = JasmineLikeAdapter()
    if (jsTypeOf(kotlinTestNamespace) != "undefined") {
        kotlinTestNamespace.adapterTransformer?.invoke(originalAdapter) ?: originalAdapter
    } else {
        originalAdapter
    }
}

// Part of the Kotlin/JS test infra.
private external interface KotlinTestNamespace {
    val adapterTransformer: ((FrameworkAdapter) -> FrameworkAdapter)?
}

// Part of the Kotlin/JS test infra.
@JsName("kotlinTest")
private external val kotlinTestNamespace: KotlinTestNamespace

private class JasmineLikeAdapter : FrameworkAdapter {
    override fun suite(name: String, ignored: Boolean, suiteFn: () -> Unit) {
        if (ignored) {
            xdescribe(name, suiteFn)
        } else {
            describe(name, suiteFn)
        }
    }

    override fun test(name: String, ignored: Boolean, testFn: () -> JsPromiseLike?) {
        if (ignored) {
            xit(name, testFn)
        } else {
            it(name, testFn)
        }
    }
}

// Jasmine/Mocha/Jest test API

@Suppress("unused")
private fun describe(description: String, suiteFn: () -> Unit) {
    // Here we disable the default 2s timeout.
    // The strange invocation is necessary to avoid using a JS arrow function which would bind `this` to a
    // wrong scope: https://stackoverflow.com/a/23492442/2529022
    js("describe(description, function () { this.timeout(0); suiteFn(); })")
}

@Suppress("unused")
private external fun xdescribe(name: String, testFn: () -> Unit)

@Suppress("unused")
private external fun it(name: String, testFn: () -> JsPromiseLike?)

@Suppress("unused")
private external fun xit(name: String, testFn: () -> JsPromiseLike?)
