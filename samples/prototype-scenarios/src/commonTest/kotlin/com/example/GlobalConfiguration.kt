package com.example

import com.example.testLibrary.statisticsReport
import testFramework.TestSession

// Specify the global configuration with a class deriving from `TestSession`.
class MyTestSession : TestSession(configuration = DefaultConfiguration.statisticsReport())
