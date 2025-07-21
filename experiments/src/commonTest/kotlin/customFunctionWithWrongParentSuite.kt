import de.infix.testBalloon.framework.TestDiscoverable
import de.infix.testBalloon.framework.TestSuite
import de.infix.testBalloon.framework.testSuite

val outer by testSuite {
    @TestDiscoverable
    fun customTest1(name: String, action: suspend () -> Unit) = test(name) {
        action()
    }

    @TestDiscoverable
    fun TestSuite.customTest2(name: String, action: suspend () -> Unit) = test(name) {
        action()
    }

    testSuite("inner") {
        customTest1("not OK") { // registering a test in `outer`, invoked in `inner`
        }
        customTest2("OK") {
        }
    }
}
