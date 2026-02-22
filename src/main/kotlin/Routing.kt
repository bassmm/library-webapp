package com.library

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.pebbletemplates.pebble.loader.ClasspathLoader
import org.jetbrains.exposed.sql.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respond(PebbleContent("base.html", mapOf("user" to "Library User")))
        }

        get("/book") {
            val placeholderBook =
                mapOf(
                    "title" to "The Book of Strange New Things",
                    "author" to "Michel Faber",
                    "notes" to "This book kinda sucks",
                )
            call.respond(PebbleContent("book.html", mapOf("book" to placeholderBook)))
        }
    }
}
