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
import org.jetbrains.exposed.v1.core.*

fun Application.configureSecurity() {
    install(Authentication) {
        form("auth-form") {
            val user = UserService.findUserByUsername(credentials.name)
            validate { credentials ->
                if (user != null && UserService.verifyPassword(credentials.password) == true) {
                    UserIdPrincipal(user.username_)
                } else {
                    null
                }
            }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, "Details are not correct")
            }
        }
    }
}
