package testFramework.internal

import testFramework.TestPlatformJsHosted
import testFramework.testPlatform

private val runsInBrowser: Boolean =
    (testPlatform as? TestPlatformJsHosted)?.runtime == TestPlatformJsHosted.Runtime.BROWSER

actual fun printlnFixed(message: Any?) {
    if (runsInBrowser) {
        println(message.toString().replace("\n", "\n\n") + "\n")
    } else {
        println(message)
    }
}
