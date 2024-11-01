package fakeTestFramework

import testFramework.AbstractTestSession
import testFramework.TestDiscoverable

@TestDiscoverable
open class FakeTestSession :
    FakeTestSuite(simpleNameOrNull = "FakeTestSession"),
    AbstractTestSession
