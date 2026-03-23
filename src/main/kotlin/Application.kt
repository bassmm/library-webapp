package com.library
import com.library.database.DatabaseCreation

import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSecurity()
    configureTemplating()
    configureSerialization()
    DatabaseCreation.init()
    configureRouting()
}
