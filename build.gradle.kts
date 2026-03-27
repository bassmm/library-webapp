val kotlinVersion = "2.3.0"
val logbackVersion = "1.5.20"
val ktor_version = "2.1.3"
val sqliteVersion = "3.51.2.0"
val exposedVersion = "1.1.1"

plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.4.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

group = "com.library"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

ktor {
    development = true
}

kotlin {
    jvmToolchain(21)
}

tasks.named<JavaExec>("run") {
    // Keeps stdin open so the process doesn't exit immediately,
    // and passes the development flag as a JVM system property
    // as a belt-and-suspenders complement to the ktor {} block above.
    standardInput = System.`in`
    jvmArgs("-Dio.ktor.development=true")
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-host-common")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-sessions:$ktor_version")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")

    // Exposed Dependencies
    implementation("org.jetbrains.exposed:exposed-core:${exposedVersion}") // foundational database components
    implementation("org.jetbrains.exposed:exposed-jdbc:${exposedVersion}") // Java database connectivity support
    implementation("org.jetbrains.exposed:exposed-java-time:${exposedVersion}") // Contains date

    // SQLite dependency Source: https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")

    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.pebble)
}
