import com.example.TestSuite1
import com.example.TestSuite2
import com.example.TestSuite3
import testFramework.internal.integration.runTests

// A compiler plugin could generate this function.
suspend fun main() {
    runTests(TestSuite1(), TestSuite2(), TestSuite3()) // <- A compiler plugin could generate this.
}
