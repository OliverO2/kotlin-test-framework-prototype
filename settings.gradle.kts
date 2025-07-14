pluginManagement {
    includeBuild("build-logic")
    includeBuild("build-settings")
}

plugins {
    id("buildSettings")
}

rootProject.name = "testBalloon"

include(":testBalloon-framework-abstractions")
include(":testBalloon-framework-core")
include(":testBalloon-gradle-plugin")
include(":testBalloon-compiler-plugin")

include(":integration-test")

include(":testBalloon-integration-kotest-assertions")
include(":testBalloon-integration-blocking-detection")

include(":examples:framework-core")

include(":examples:integration-kotest-assertions")

include(":comparisons:using-kotlin-test")
include(":comparisons:using-testBalloon")

include(":experiments")
