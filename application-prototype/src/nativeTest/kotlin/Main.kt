import com.example.TestSuite1
import com.example.TestSuite2
import com.example.TestSuite3
import kotlinx.coroutines.runBlocking
import testFramework.TestModule

// A compiler plugin could generate this declaration.
// Deprecation tracked in https://youtrack.jetbrains.com/issue/KT-63218/EagerInitialization-use-cases
@Suppress("DEPRECATION", "unused")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val main = runBlocking {
    // Kotlin/Native does not seem to supply an entry point for tests. So we use a simple property's initialization
    // to execute the test module. The property must be marked with @EagerInitialization, otherwise it would wait
    // for lazy initialization, which never happens as the property is not referenced anywhere.
    TestModule.execute(TestSuite1(), TestSuite2(), TestSuite3()) // <- A compiler plugin could generate this.
}
