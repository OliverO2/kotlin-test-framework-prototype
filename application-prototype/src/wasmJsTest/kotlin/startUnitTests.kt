import kotlin.wasm.WasmExport

// This symbol is required by the Kotlin Gradle plugin. It is not used by this test framework.
@Suppress("unused")
@WasmExport
fun startUnitTests() {}
