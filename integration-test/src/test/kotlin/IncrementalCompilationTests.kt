import de.infix.testBalloon.framework.AbstractTestElement
import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.TestDiscoverable
import de.infix.testBalloon.framework.TestSuite
import de.infix.testBalloon.framework.disable
import de.infix.testBalloon.framework.testPlatform
import de.infix.testBalloon.framework.testScope
import de.infix.testBalloon.framework.testSuite
import kotlinx.datetime.Clock
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.buildList
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

val IncrementalCompilationTests by testSuite(
    testConfig = TestConfig.testScope(isEnabled = true, timeout = 12.minutes)
) {
    val kotlinTestConfig = TestConfig.disable()
    val fullCompilationConfig = TestConfig.disable()

    testScenario("incremental-compilation-kotlin-test", testConfig = kotlinTestConfig) {
        variant("incremental compilation")
    }

    testScenario("incremental-compilation-testBalloon") {
        variant(
            name = "full compilation",
            gradleOptions = arrayOf(
                "-Pkotlin.incremental=false",
                "-Pkotlin.incremental.js=false",
                "-Pkotlin.incremental.js.klib=false",
                "-Pkotlin.incremental.js.ir=false"
            ),
            testConfig = fullCompilationConfig
        )

        variant("incremental compilation")
    }
}

@TestDiscoverable
private fun TestSuite.testScenario(
    scenarioName: String,
    testConfig: TestConfig = TestConfig,
    action: TestScenario.() -> Unit
) = testSuite(scenarioName, displayName = "scenario: $scenarioName", testConfig) {
    val commonTemplateDirectory = Path("build/scenarioTemplates/common")
    val scenarioTemplateDirectory = Path("build/scenarioTemplates/$scenarioName")
    val projectDirectory = Path("build/scenarioProjects/$scenarioName")

    @OptIn(ExperimentalPathApi::class)
    aroundAll { testSuiteAction ->
        log("Setting up $projectDirectory from $commonTemplateDirectory, $scenarioTemplateDirectory")
        if (projectDirectory.exists()) projectDirectory.deleteRecursively()
        projectDirectory.createDirectories()
        commonTemplateDirectory.copyToRecursively(projectDirectory, followLinks = false, overwrite = false)
        scenarioTemplateDirectory.copyToRecursively(projectDirectory, followLinks = false, overwrite = false)
        testSuiteAction()
    }

    TestScenario(this@testSuite, projectDirectory).action()
}

private class TestScenario(private val parentTestSuite: TestSuite, private val projectDirectory: Path) {
    private val commonTestSourceDirectory = projectDirectory / "src/commonTest"
    private val enabledSourcesDirectory = commonTestSourceDirectory / "kotlin"
    private val disabledSourcesDirectory = commonTestSourceDirectory / "disabled"

    private val taskNames = parentTestSuite.testFixture {
        gradleExecution(projectDirectory, "listTests", "--quiet").checkedStdout().lines()
    }

    private val npmPackageLockTasks = parentTestSuite.testFixture {
        buildList {
            if (taskNames().any { it.startsWith("js") }) add("kotlinUpgradePackageLock")
        }.toTypedArray()
    }

    fun variant(name: String, gradleOptions: Array<String> = arrayOf(), testConfig: TestConfig = TestConfig) =
        parentTestSuite.testSuite(name, testConfig = testConfig) {
            fun taskExecution(taskName: String) = gradleExecution(projectDirectory, taskName, *gradleOptions)

            aroundAll { testSuiteAction ->
                gradleExecution(projectDirectory, "clean", *npmPackageLockTasks()).checked()
                testSuiteAction()
            }

            val fileCount = 2
            val nativeTargetsThatMayFail = listOf("macosX64", "linuxX64", "mingwX64")

            val baselineResults = testFixture {
                taskNames().mapNotNull { taskName ->
                    val taskExecution = taskExecution(taskName)
                    val targetName = taskName.removeSuffix("Test")

                    if (targetName in nativeTargetsThatMayFail &&
                        (
                            taskExecution.stdout.contains(":$taskName SKIPPED") ||
                                taskExecution.stderr.contains(
                                    "Could not resolve all artifacts for configuration ':$targetName"
                                )
                            )
                    ) {
                        return@mapNotNull null
                    }

                    val taskResults = taskExecution.logMessages()
                    check(taskResults.size == fileCount) {
                        "$taskName was expected to produce $fileCount results, but produced ${taskResults.size}:\n" +
                            "\tactual results:\n${taskResults.asIndentedText(indent = "\t\t")}\n" +
                            taskExecution.stdoutStderr()
                    }

                    taskName to taskResults
                }.toMap()
            }

            test("baseline") {
                check(baselineResults().isNotEmpty()) {
                    "None of the tasks ${taskNames()} produced a result."
                }
            }

            suspend fun AbstractTestElement.verifyResults(taskName: String, exceptIndex: Int? = null) {
                val baselineResult = baselineResults()[taskName] ?: return
                val expectedResults = baselineResult.filterIndexed { index, _ ->
                    index != exceptIndex
                }
                val taskExecution = taskExecution(taskName)
                val actualResults = taskExecution.logMessages()
                if (actualResults != expectedResults) {
                    throw AssertionError(
                        "$taskName: Results do not meet expectations.\n" +
                            "\tExpected: $expectedResults\n" +
                            "\tActual: ${actualResults}\n" + taskExecution.stdoutStderr()
                    )
                }
                println("$testElementDisplayName: $taskName – OK")
            }

            val fileIndex = 0
            val fileNameToMove = "File1.kt"

            test("remove $fileNameToMove") {
                (enabledSourcesDirectory / fileNameToMove).moveTo(disabledSourcesDirectory / fileNameToMove)
                for (taskName in taskNames()) {
                    verifyResults(taskName, fileIndex)
                }
            }

            test("restore $fileNameToMove") {
                (disabledSourcesDirectory / fileNameToMove).moveTo(enabledSourcesDirectory / fileNameToMove)
                for (taskName in taskNames()) {
                    verifyResults(taskName)
                }
            }
        }
}

private fun List<String>.asIndentedText(indent: String = "\t") = joinToString(prefix = indent, separator = "\n$indent")

private fun gradleExecution(projectDirectory: Path, vararg arguments: String): Execution = execution(
    "${projectDirectory.pathString}/gradlew",
    "-p",
    projectDirectory.pathString,
    *arguments
)

private fun execution(vararg arguments: String): Execution {
    val process = ProcessBuilder(*arguments).also {
        it.environment().run {
            val keysToRemove = keys.mapNotNull { key ->
                if (key in listOf("JAVA_HOME", "PATH", "LANG", "SHELL", "TERM") || key.startsWith("LC_")) {
                    null
                } else {
                    key
                }
            }
            for (key in keysToRemove) {
                remove(key)
            }
        }
    }.start()

    val stdout = process.inputStream.readAllBytes().toString(Charsets.UTF_8).trim()
    val stderr = process.errorStream.readAllBytes().toString(Charsets.UTF_8).trim()
    val exitCode = process.waitFor()

    return Execution(arguments.toList(), exitCode, stdout, stderr).run {
        log("Execution ${this.arguments} returned exit code $exitCode\n${stdoutStderr("\t")}")
        this
    }
}

private data class Execution(val arguments: List<String>, val exitCode: Int, val stdout: String, val stderr: String) {
    private val logMessageRegex = Regex("""##LOG\((.*?)\)LOG##""")

    fun logMessages(): List<String> = logMessageRegex.findAll(checkedStdout()).mapNotNull {
        it.groups[1]?.value
    }.toList()

    fun stdoutStderr(indent: String = "\t") = "$indent--- stdout ---\n${stdout.prependIndent("$indent\t")}\n" +
        "$indent--- stderr ---${stderr.prependIndent("$indent\t")}"

    fun checked(): Execution {
        check(exitCode == 0) {
            "Execution $arguments failed with exit code $exitCode\n" + stdoutStderr("\t")
        }
        return this
    }

    fun checkedStdout(): String = checked().stdout
}

private const val LOG_ENABLED = true
private val logDirectory = Path("build/reports").also { it.toFile().mkdirs() }
private val logFile = (logDirectory / "TestScenario.log").toFile()
private val logInitialized = AtomicBoolean(false)

private fun log(message: String) {
    @Suppress("KotlinConstantConditions")
    if (!LOG_ENABLED) return

    if (!logInitialized.getAndSet(true)) {
        logFile.appendText("\n––– Session Starting –––\n")
    }

    logFile.appendText("${Clock.System.now()} [${testPlatform.threadId()}] $message\n")
}
