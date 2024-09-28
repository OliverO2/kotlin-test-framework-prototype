import com.example.TestSuite1
import com.example.TestSuite2
import com.example.TestSuite3
import testFramework.internal.integration.runTests

suspend fun main() {
    runTests(TestSuite1(), TestSuite2(), TestSuite3()) // <- A compiler plugin could generate this.
}

// The Wasm/WASI launcher set up by the Kotlin Gradle plugin calls this function in addition to `main()`.
@Suppress("unused")
@WasmExport
internal fun startUnitTests() {
    // The Kotlin compiler would insert test invocations here for kotlin-test.
    // This mechanism is not used by this framework.
}
