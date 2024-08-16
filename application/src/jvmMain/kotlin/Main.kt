import kotlinx.coroutines.runBlocking
import testFramework.Module

// A compiler plugin could generate this function.
fun main() = runBlocking {
    runTests(Module.default)
}
