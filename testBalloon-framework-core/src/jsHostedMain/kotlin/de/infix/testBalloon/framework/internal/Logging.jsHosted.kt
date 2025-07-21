package de.infix.testBalloon.framework.internal

import de.infix.testBalloon.framework.TestPlatformJsHosted
import de.infix.testBalloon.framework.testPlatform

internal val runsInBrowser: Boolean =
    (testPlatform as? TestPlatformJsHosted)?.runtime == TestPlatformJsHosted.Runtime.BROWSER

actual fun printlnFixed(message: Any?) {
    if (runsInBrowser) {
        println(message.toString().replace("\n", "\n\n") + "\n")
    } else {
        println(message)
    }
}
