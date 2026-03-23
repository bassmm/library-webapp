val kotlinVersion = "2.3.0"
val logbackVersion = "1.5.20"

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

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-host-common")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.ktor:ktor-server-config-yaml")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")

    // Exposed Dependencies
    implementation("org.jetbrains.exposed:exposed-core:${exposedVersion}") // foundational database components
    implementation("org.jetbrains.exposed:exposed-jdbc:${exposedVersion}") // Java database connectivity support
    implementation("org.jetbrains.exposed:exposed-java-time:${exposedVersion}") // Contains date

    // SQLite dependency Source: https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")

    // DataFaker Source: https://mvnrepository.com/artifact/net.datafaker/datafaker
    implementation("net.datafaker:datafaker:2.5.4")

}
