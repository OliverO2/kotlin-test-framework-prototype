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
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class CompilerPluginTests {
    @Test
    fun resolution() {
        listOf("com.example", "").forEach { packageName ->
            println("=== packageName='$packageName' ===")

            val packageDeclaration = if (packageName.isEmpty()) "" else "package $packageName"
            val packageNameDot = if (packageName.isEmpty()) "" else "$packageName."
            val d = "$"

            compilation(
                """
                    $packageDeclaration
                    
                    import kotlinx.coroutines.runBlocking
                    import testFramework.annotations.TestDeclaration

                    @TestDeclaration
                    class TestSuiteOne {
                        init {
                            println("$d{this::class.qualifiedName}")
                        }
                    }

                    @TestDeclaration
                    class TestSuiteTwo {
                        init {
                            println("$d{this::class.qualifiedName}")
                        }
                    }
                """,
                debugEnabled = true,
                jvmStandaloneEnabled = true
            ) {
                println("--- Messages ---")
                println(messages)

                val entryPointClass = classLoader.loadClass("${packageNameDot}MainKt")
                val capturedStdout = capturedStdout {
                    runBlocking {
                        entryPointClass.getDeclaredMethod("main", Continuation::class.java).invoke(
                            entryPointClass,
                            Continuation<Unit>(currentCoroutineContext()) {}
                        )
                    }
                }

                println("--- Standard Output ---")
                println(capturedStdout)

                assertTrue(
                    """
                        ${packageNameDot}TestSuiteOne
                        ${packageNameDot}TestSuiteTwo
                    """.trimIndent() in capturedStdout,
                    capturedStdout
                )
            }
        }
    }

    @Test
    fun debugEnabled() {
        compilation(
            """
                import testFramework.annotations.TestDeclaration

                @TestDeclaration
                class TestSuiteOne
            """,
            debugEnabled = true
        ) {
            println(messages)
            assertTrue("[DEBUG] Found test suite 'TestSuiteOne'" in messages)
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
            println(messages)
            assertTrue("Please add the corresponding library dependency." in messages)
        }
    }
}

@OptIn(ExperimentalCompilerApi::class)
private fun compilation(
    sourceCode: String,
    debugEnabled: Boolean = false,
    jvmStandaloneEnabled: Boolean = false,
    classPathInheritanceEnabled: Boolean = true,
    expectedExitCode: KotlinCompilation.ExitCode = KotlinCompilation.ExitCode.OK,
    action: JvmCompilationResult.() -> Unit
) {
    val compilation = KotlinCompilation()

    fun option(name: String, value: String): PluginOption =
        PluginOption(BuildConfig.TEST_FRAMEWORK_PLUGIN_ID, name, value)

    try {
        val result = compilation.apply {
            sources = listOf(SourceFile.kotlin("Main.kt", sourceCode.trimIndent()))
            verbose = false
            compilerPluginRegistrars = listOf(CompilerPluginRegistrar())
            inheritClassPath = classPathInheritanceEnabled
            commandLineProcessors = listOf(CompilerPluginCommandLineProcessor())
            pluginOptions = listOfNotNull(
                if (debugEnabled) option("debug", "true") else null,
                if (jvmStandaloneEnabled) option("jvmStandalone", "true") else null
            )
            messageOutputStream = OutputStream.nullOutputStream()
        }.compile()

        assertEquals(expectedExitCode, result.exitCode, result.messages)

        result.action()
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
