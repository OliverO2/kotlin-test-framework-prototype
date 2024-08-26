import com.example.TestScope1
import com.example.TestScope2
import com.example.TestScope3
import testFramework.TestModule

// A compiler plugin could generate this function.
suspend fun main() {
    TestModule.execute(TestScope1(), TestScope2(), TestScope3()) // <- A compiler plugin could generate this.
}
