package fakeTestFramework

import testFramework.AbstractTestSession
import testFramework.TestDiscoverable

@TestDiscoverable
open class TestSession :
    TestSuite(elementName = "TestSession"),
    AbstractTestSession
