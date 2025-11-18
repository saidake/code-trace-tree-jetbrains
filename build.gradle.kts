plugins {
    kotlin("jvm") version "1.9.24" apply false
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "8.13"
}

allprojects {
    repositories {
        mavenCentral()
    }
}
