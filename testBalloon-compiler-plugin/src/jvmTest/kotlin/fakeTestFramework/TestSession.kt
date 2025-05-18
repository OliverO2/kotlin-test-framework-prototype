package fakeTestFramework

import de.infix.testBalloon.framework.AbstractTestSession
import de.infix.testBalloon.framework.TestDiscoverable

@Suppress("unused")
@TestDiscoverable
open class TestSession :
    TestSuite(name = "TestSession"),
    AbstractTestSession
