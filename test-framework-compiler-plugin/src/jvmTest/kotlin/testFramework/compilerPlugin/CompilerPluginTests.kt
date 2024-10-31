package testFramework.compilerPlugin

import buildConfig.BuildConfig
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.incremental.deleteRecursivelyOrThrow
import testFramework.TestSession
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
private class CompilerPluginTests {
    @Test
    fun suiteDiscovery() {
        listOf("com.example", "").forEach { packageName ->
            println("=== packageName='$packageName' ===")

            val packageDeclaration = if (packageName.isEmpty()) "" else "package $packageName"

            compilation(
                """
                    $packageDeclaration
                    
                    import testFramework.TestSuite
                    import testFramework.suite
                    import kotlin.getValue

                    class TestSuiteOne : TestSuite({})

                    class TestSuiteTwo : TestSuite({})

                    val testSuiteThree by suite("testSuiteThree") {}
                """,
                debugEnabled = true
            ) {
                val packageNameDot = if (packageName.isEmpty()) "" else "$packageName."

                assertTrue("Found test discoverable '${packageNameDot}TestSuiteOne'" in messages)
                assertTrue("Found test discoverable '${packageNameDot}TestSuiteTwo'" in messages)
                assertTrue("Found test discoverable '${packageNameDot}testSuiteThree'" in messages)
            }
        }
    }

    @Test
    fun initialization() {
        val packageName = "com.example"

        val d = "$"

        compilation(
            """
                package $packageName
                
                import testFramework.TestSession
                import testFramework.TestSuite
                import testFramework.suite
                import kotlin.getValue

                class TestSuiteOne : TestSuite({
                    println("$d{this::class.qualifiedName}")
                })
                
                class MyTestSession : TestSession() {
                    init {
                        println("$d{this::class.qualifiedName}")
                    }
                }

                class TestSuiteTwo : TestSuite({
                    println("$d{this::class.qualifiedName}")
                })

                val testSuiteThree by suite("testSuiteThree") {
                    println("$d{this::class.qualifiedName}")
                }
            """,
            debugEnabled = true,
            executionPackageName = packageName
        ) { capturedStdout ->

            assertTrue(
                """
                    $packageName.MyTestSession
                    $packageName.TestSuiteOne
                    $packageName.TestSuiteTwo
                    testFramework.TestSuite
                """.trimIndent() in capturedStdout,
                capturedStdout
            )
        }
    }

    @Test
    fun insistOnSingleTestSession() {
        compilation(
            """
                package com.example
                
                import testFramework.TestSession
                import testFramework.TestSuite

                class TestSuiteOne : TestSuite({})
                
                class MyTestSession : TestSession()

                class TestSuiteTwo : TestSuite({})
                
                class MyOtherTestSession : TestSession()
            """,
            expectedExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR
        ) {
            assertTrue("Found multiple test sessions annotated with @TestDiscoverable" in messages)
        }
    }

    @Test
    fun debugEnabled() {
        compilation(
            """
                import testFramework.TestSuite
                
                class TestSuiteOne : TestSuite({})
            """,
            debugEnabled = true
        ) {
            assertTrue("[DEBUG] Found test discoverable 'TestSuiteOne'" in messages)
        }
    }

    @Test
    fun missingAnnotationsLibraryDependency() {
        compilation(
            """
                val foo = 1
            """,
            classPathInheritanceEnabled = false,
            expectedExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR
        ) {
            assertTrue("Please add the corresponding library dependency." in messages)
        }
    }
}

@OptIn(ExperimentalCompilerApi::class)
private fun compilation(
    sourceCode: String,
    debugEnabled: Boolean = false,
    executionPackageName: String? = null,
    classPathInheritanceEnabled: Boolean = true,
    expectedExitCode: KotlinCompilation.ExitCode = KotlinCompilation.ExitCode.OK,
    action: JvmCompilationResult.(capturedStdout: String) -> Unit
) {
    val compilation = KotlinCompilation()

    fun option(name: String, value: String): PluginOption =
        PluginOption(BuildConfig.TEST_FRAMEWORK_PLUGIN_ID, name, value)

    try {
        compilation.apply {
            sources = listOf(SourceFile.kotlin("Main.kt", sourceCode.trimIndent()))
            verbose = false
            compilerPluginRegistrars = listOf(CompilerPluginRegistrar())
            inheritClassPath = classPathInheritanceEnabled
            commandLineProcessors = listOf(CompilerPluginCommandLineProcessor())
            pluginOptions = listOfNotNull(
                if (debugEnabled) option("debug", "true") else null,
                if (executionPackageName != null) option("jvmStandalone", "true") else null
            )
            messageOutputStream = OutputStream.nullOutputStream()
        }.compile().run {
            println("--- Compilation ---")
            println(messages)

            assertEquals(expectedExitCode, exitCode, messages)

            var capturedStdout = ""

            if (executionPackageName != null) {
                // The following hack un-initializes the framework. This is necessary because the framework's classes
                // are loaded into the test JVM. JVM-static members of such classes will then retain their state
                // across test runs, but the framework expects such members to have a freshly initialized state
                // on each run.
                // FIXME: Loading `TestSessionKt` multiple times leads to inconsistencies regarding compartments.
                //     TestCompartments used repeatedly in multiple test runs will have an earlier test run's
                //     TestSession as their parent. It would be better to load MainKt and all dependencies in an
                //     isolated class loader per invocation. See https://stackoverflow.com/a/3726742/2529022
                @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
                TestSession.singleton = null

                val packageNameDot = if (executionPackageName.isEmpty()) "" else "$executionPackageName."
                val entryPointClass = classLoader.loadClass("${packageNameDot}MainKt")
                capturedStdout = capturedStdout {
                    runBlocking {
                        entryPointClass.getDeclaredMethod(
                            "main",
                            Array<String>::class.java,
                            Continuation::class.java
                        ).invoke(
                            entryPointClass,
                            null,
                            Continuation<Unit>(currentCoroutineContext()) {}
                        )
                    }
                }

                println("--- Execution ---")
                println(capturedStdout)
            }

            action(capturedStdout)
        }
    } finally {
        compilation.workingDir.deleteRecursivelyOrThrow()
    }
}

private fun capturedStdout(action: () -> Unit): String {
    val originalStdout = System.out
    val stdoutCapturingStream = ByteArrayOutputStream()
    System.setOut(PrintStream(stdoutCapturingStream))

    action()

    System.setOut(originalStdout)
    return stdoutCapturingStream.toString()
}
