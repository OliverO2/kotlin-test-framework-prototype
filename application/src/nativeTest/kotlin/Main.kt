import com.example.TestScope1
import com.example.TestScope2
import com.example.TestScope3
import kotlinx.coroutines.runBlocking
import testFramework.TestModule

// A compiler plugin could generate this declaration.
// Deprecation tracked in https://youtrack.jetbrains.com/issue/KT-63218/EagerInitialization-use-cases
@Suppress("DEPRECATION")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
val main = object {
    // Kotlin/Native does not seem to supply an entry point for tests. So we use a simple property's initialization
    // to execute the test module. The property must be marked with @EagerInitialization, otherwise it would wait
    // for lazy initialization, which never happens as the property is not referenced anywhere.
    init {
        runBlocking {
            TestModule.execute(TestScope1(), TestScope2(), TestScope3()) // <- A compiler plugin could generate this.
        }
    }
}
