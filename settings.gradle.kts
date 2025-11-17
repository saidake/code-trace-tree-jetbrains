plugins {
    // This is the only correct place for foojay
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "code-trace-tree"

include("main")
