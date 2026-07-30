plugins {
    kotlin("jvm") version "2.1.10" apply false
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "8.13"
}

allprojects {
    repositories {
        mavenCentral()
    }
}
