package fakeTestFramework

import testFramework.AbstractTestSession
import testFramework.TestDiscoverable

@Suppress("unused")
@TestDiscoverable
open class TestSession :
    TestSuite(elementName = "TestSession"),
    AbstractTestSession
