import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.testScope
import de.infix.testBalloon.framework.testSuite
import kotlin.time.Duration.Companion.minutes

val IncrementalCompilationTests by testSuite(testConfig = TestConfig.testScope(isEnabled = true, timeout = 3.minutes)) {
    testScenario("incremental-compilation-testBalloon") {
        variant(
            "full compilation",
            "-Pkotlin.incremental=false",
            "-Pkotlin.incremental.js=false",
            "-Pkotlin.incremental.js.klib=false",
            "-Pkotlin.incremental.js.ir=false"
        )

        variant("incremental compilation")
    }

    testScenario("incremental-compilation-kotlin-test") {
        variant("incremental compilation")
    }
}
