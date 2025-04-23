package com.example

import com.example.testLibrary.statisticsReport
import de.infix.testBalloon.framework.TestSession

// Specify the global configuration with a class deriving from `TestSession`.
class MyTestSession : TestSession(testConfig = DefaultConfiguration.statisticsReport())
