plugins {
    kotlin("jvm") version "1.9.24" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "8.13"
}

allprojects {
    repositories {
        mavenCentral()
    }
}
// Apply ktlint to root + all subprojects
apply(plugin = "org.jlleitschuh.gradle.ktlint")

// Global ktlint configuration (applies to root and all subprojects)
ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    coloredOutput.set(true)
    ignoreFailures.set(false) // fails the build on violations
    // ktlint already includes *.kt and *.kts files anywhere in src/main/kotlin, src/test/kotlin, etc.
    filter {
        exclude("**/generated/**")
    }
}
