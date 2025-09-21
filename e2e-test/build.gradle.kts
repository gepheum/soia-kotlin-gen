plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

kotlin {
    compilerOptions {
        // Removed unsupported flag
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okio:okio:3.6.0")
    implementation("land.soia:soia-kotlin-client:1.0.13")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}
