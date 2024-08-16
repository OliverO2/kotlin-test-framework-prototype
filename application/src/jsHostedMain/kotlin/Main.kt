import testFramework.Module

// A compiler plugin could generate this function.
suspend fun main() {
    runTests(Module.default)
}
