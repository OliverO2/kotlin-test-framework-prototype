@file:Suppress("PackageDirectoryMismatch")

// The compiler plugin requires this package name.
package testFramework.internal

import testFramework.AbstractTestSession
import testFramework.AbstractTestSuite

internal fun initializeTestFramework(testSession: AbstractTestSession?, arguments: Array<String>? = null) {}

internal suspend fun runTests(suites: Array<AbstractTestSuite>) {}
