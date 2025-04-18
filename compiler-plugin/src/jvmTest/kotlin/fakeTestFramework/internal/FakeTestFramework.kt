@file:Suppress("PackageDirectoryMismatch", "unused")

// The compiler plugin requires this package name.

package de.infix.testBalloon.framework.internal

import de.infix.testBalloon.framework.AbstractTestSession
import de.infix.testBalloon.framework.AbstractTestSuite
import de.infix.testBalloon.framework.InvokedByGeneratedCode

@InvokedByGeneratedCode
internal fun initializeTestFramework(testSession: AbstractTestSession?, arguments: Array<String>? = null) {}

@Suppress("RedundantSuspendModifier")
@InvokedByGeneratedCode
internal suspend fun configureAndExecuteTests(suites: Array<AbstractTestSuite>) {}
