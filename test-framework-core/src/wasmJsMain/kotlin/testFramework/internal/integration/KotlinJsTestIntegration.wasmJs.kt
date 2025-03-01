package testFramework.internal.integration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import testFramework.TestPlatformJsHosted
import testFramework.TestPlatformWasmJs
import kotlin.js.Promise

internal actual fun kotlinJsTestFrameworkAvailable(): Boolean =
    js("typeof describe === 'function' && typeof it === 'function'")

internal actual val kotlinJsTestFramework: KotlinJsTestFramework = object : KotlinJsTestFramework {
    override fun suite(name: String, ignored: Boolean, suiteFn: () -> Unit) {
        if (ignored) {
            xdescribe(name, suiteFn)
        } else {
            describe(name, suiteFn)
        }
    }

    override fun test(name: String, ignored: Boolean, testFn: () -> JsPromiseLike?) {
        if (ignored) {
            xit(name) {
                callTest(testFn)
            }
        } else {
            it(name) {
                callTest(testFn)
            }
        }
    }

    private fun callTest(testFn: () -> JsPromiseLike?): Promise<*>? = try {
        (testFn() as? Promise<*>)?.catch { exception ->
            val jsException = exception
                .toThrowableOrNull()
                ?.toJsError()
                ?: exception
            Promise.reject(jsException)
        }
    } catch (exception: Throwable) {
        jsThrow(exception.toJsError())
    }
}

internal actual fun CoroutineScope.testFunctionPromise(testFunction: suspend () -> Unit): JsPromiseLike? =
    promise { testFunction() }

internal actual fun processArguments(): Array<String>? {
    if (TestPlatformWasmJs.runtime != TestPlatformJsHosted.Runtime.NODE) return null
    val rawArguments = nodeProcessArguments()
    return Array(rawArguments.length) { rawArguments[it].toString() }
}

private fun nodeProcessArguments(): JsArray<JsString> = js("process.argv.slice(2)")

@Suppress("unused")
private fun jsThrow(jsException: JsAny): Nothing = js("{ throw jsException; }")

@Suppress("unused")
private fun throwableToJsError(message: String, stack: String): JsAny =
    js("{ const e = new Error(); e.message = message; e.stack = stack; return e; }")

private fun Throwable.toJsError(): JsAny = throwableToJsError(message ?: "", stackTraceToString())

// Jasmine test framework functions

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
private external fun it(name: String, testFn: () -> JsAny?)

@Suppress("unused")
private external fun xit(name: String, testFn: () -> JsAny?)
