import de.infix.testBalloon.framework.TestDiscoverable
import de.infix.testBalloon.framework.TestSuite
import de.infix.testBalloon.framework.testPlatform
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@TestDiscoverable
fun TestSuite.testScenario(scenarioName: String, action: TestScenario.() -> Unit) = testSuite(scenarioName) {
    val commonTemplateDirectory = Path("build/scenarios/common")
    val scenarioTemplateDirectory = Path("build/scenarios/$scenarioName")
    val projectDirectory = Path("build/scenarioProjects/$scenarioName")

    @OptIn(ExperimentalPathApi::class)
    aroundAll { testSuiteAction ->
        if (projectDirectory.exists()) projectDirectory.deleteRecursively()
        projectDirectory.createDirectories()
        commonTemplateDirectory.copyToRecursively(projectDirectory, followLinks = false, overwrite = false)
        scenarioTemplateDirectory.copyToRecursively(projectDirectory, followLinks = false, overwrite = false)
        testSuiteAction()
    }

    TestScenario(this@testSuite, projectDirectory).action()
}

class TestScenario(private val parentTestSuite: TestSuite, private val projectDirectory: Path) {
    private val commonTestSourceDirectory = projectDirectory / "src/commonTest"
    private val enabledSourcesDirectory = commonTestSourceDirectory / "kotlin"
    private val disabledSourcesDirectory = commonTestSourceDirectory / "disabled"

    private val taskNames = parentTestSuite.testFixture {
        gradleExecution(projectDirectory, "listTests", "--quiet").checkedStdout().lines()
    }

    fun variant(name: String, vararg gradleOptions: String) = parentTestSuite.testSuite(name) {
        fun taskExecution(taskName: String) = gradleExecution(projectDirectory, taskName, *gradleOptions)

        aroundAll { testSuiteAction ->
            gradleExecution(projectDirectory, "clean", "kotlinUpgradeYarnLock", "kotlinWasmUpgradeYarnLock").checked()
            testSuiteAction()
        }

        val fileCount = 3

        val baselineResults = testFixture {
            taskNames().associateWith { taskName ->
                val taskExecution = taskExecution(taskName)
                val taskResults = taskExecution.logMessages()
                check(taskResults.size == fileCount) {
                    "$taskName was expected to produce $fileCount results, but produced ${taskResults.size}:\n" +
                        "\tactual results:\n${taskResults.asIndentedText(indent = "\t\t")}\n" +
                        taskExecution.stdoutStderr()
                }
                taskResults
            }
        }

        test("baseline") {
            check(baselineResults().isNotEmpty()) {
                "None of the tasks ${taskNames()} produced a result."
            }
        }

        suspend fun verifyResults(taskName: String, exceptIndex: Int? = null) {
            val expectedResults = baselineResults()[taskName]!!.filterIndexed { index, _ ->
                index != exceptIndex
            }
            val taskExecution = taskExecution(taskName)
            val actualResults = taskExecution.logMessages()
            if (actualResults != expectedResults) {
                throw AssertionError(
                    "Expected: $expectedResults, actual: ${actualResults}\n" + taskExecution.stdoutStderr()
                )
            }
        }

        for (fileIndex in 0 until fileCount) {
            val filePathToMove = Path("File${fileIndex + 1}.kt")

            testSuite("$filePathToMove") {
                test("remove") {
                    filePathToMove.move(enabledSourcesDirectory, disabledSourcesDirectory)
                    for (taskName in taskNames()) {
                        verifyResults(taskName, fileIndex)
                    }
                }

                test("restore") {
                    filePathToMove.move(disabledSourcesDirectory, enabledSourcesDirectory)
                    for (taskName in taskNames()) {
                        verifyResults(taskName)
                    }
                }
            }
        }
    }
}

private fun List<String>.asIndentedText(indent: String = "\t") = joinToString(prefix = indent, separator = "\n$indent")

private fun Path.move(source: Path, target: Path) = check((source / this).toFile().renameTo((target / this).toFile())) {
    "Could not move $this from $source to $target"
}

private fun gradleExecution(projectDirectory: Path, vararg arguments: String): Execution = execution(
    "${projectDirectory.pathString}/gradlew",
    "-p",
    projectDirectory.pathString,
    *arguments
)

private fun execution(vararg arguments: String): Execution {
    val process = ProcessBuilder(*arguments).also {
        it.environment().run {
            this.remove("TEST_INCLUDE")
            this.remove("TEST_EXCLUDE")
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

private const val LOG_ENABLED = false
private val logFile = Path("build/TestScenario.log").toFile()
private val logInitialized = AtomicBoolean(false)

private fun log(message: String) {
    @Suppress("KotlinConstantConditions")
    if (!LOG_ENABLED) return

    if (!logInitialized.getAndSet(true)) {
        log("\n––– Session Starting –––")
        val environment = System.getenv().map { "${it.key}=${it.value}" }.sorted()
        log("Environment:\n${environment.joinToString("\n\t", prefix = "\t")}")
    }

    @OptIn(ExperimentalTime::class)
    logFile.appendText("${Clock.System.now()} [${testPlatform.threadId()}] $message\n")
}
