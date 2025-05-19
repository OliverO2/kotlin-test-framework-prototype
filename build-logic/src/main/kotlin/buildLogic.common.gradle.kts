plugins {
    id("org.jmailen.kotlinter")
}

group = project.property("local.PROJECT_GROUP_ID")!!

kotlinter {
    ignoreLintFailures = false
    reporters = arrayOf("checkstyle", "plain")
}

tasks.withType<org.jmailen.gradle.kotlinter.tasks.LintTask> {
    source = source.minus(fileTree("build")).asFileTree
}

tasks.withType<org.jmailen.gradle.kotlinter.tasks.FormatTask> {
    source = source.minus(fileTree("build")).asFileTree
}
