import buildConfig.BuildConfig
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.incremental.deleteRecursivelyOrThrow
import testFramework.compilerPlugin.CompilerPluginCommandLineProcessor
import testFramework.compilerPlugin.CompilerPluginRegistrar
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
                    
                    import fakeTestFramework.FakeTestSuite
                    import fakeTestFramework.suite

                    val TestSuiteOne by suite {}

                    class TestSuiteTwo : FakeTestSuite(content = {})
                """,
                debugEnabled = true
            ) {
                val packageNameDot = if (packageName.isEmpty()) "" else "$packageName."

                assertTrue("Found test discoverable '${packageNameDot}TestSuiteOne'" in messages)
                assertTrue("Found test discoverable '${packageNameDot}TestSuiteTwo'" in messages)
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
                
                import fakeTestFramework.FakeTestSession
                import fakeTestFramework.FakeTestSuite
                import fakeTestFramework.suite

                val TestSuiteOne by suite {
                    println("$d{elementPath}")
                }
                
                class MyTestSession : FakeTestSession() {
                    init {
                        println("$d{this::class.qualifiedName}")
                    }
                }

                class TestSuiteTwo : FakeTestSuite(content = {
                    println("$d{elementPath}")
                })

                val testSuiteThree by suite("my test suite three") {
                    println("$d{elementPath}")
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
                    my test suite three
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
                
                import fakeTestFramework.FakeTestSession

                class MyTestSession : FakeTestSession()
                class MyOtherTestSession : FakeTestSession()
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
                import fakeTestFramework.suite
                
                val TestSuiteOne by suite {}
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
