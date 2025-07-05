import de.infix.testBalloon.framework.TestCoroutineScope
import de.infix.testBalloon.framework.testPlatform

fun TestCoroutineScope.log(message: String) {
    println("##LOG(${testPlatform.displayName} – $testElementPath: $message)LOG##")
}
