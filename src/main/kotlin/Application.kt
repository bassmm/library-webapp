package com.library
import com.library.database.DatabaseCreation
import com.library.database.DatabaseSeeder

import io.ktor.server.application.*
import org.jetbrains.exposed.v1.jdbc.Database

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

fun Application.module() {
    configureSecurity()
    configureTemplating()
    configureSerialization()
    DatabaseCreation.init()
    DatabaseSeeder.seedBooksFromCsv("src/main/resources/library_booklist.csv")
    configureRouting()
}
