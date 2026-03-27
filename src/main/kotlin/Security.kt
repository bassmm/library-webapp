package com.library

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database

@Serializable
data class UserSession(val username: String)

fun Application.configureSecurity() {

    val userService = UserService(Database.connect("jdbc:sqlite:data/library.db"))

    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 60 * 60 * 24
            cookie.extensions["SameSite"] = "lax"
        }
    }

    install(Authentication) {
        form("auth-form") {
            userParamName = "username"
            passwordParamName = "password"
            validate { credentials ->
                val user = userService.findUserByUsername(credentials.name)
                if (user != null && userService.verifyPassword(credentials.name, credentials.password)) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
            challenge {
                call.respondRedirect("/login_page?error=invalid")
            }
        }

        session<UserSession>("auth-session") {
            validate { session ->
                UserIdPrincipal(session.username)
            }
            challenge {
                call.respondRedirect("/login_page")
            }
        }
    }

    routing {
        get("/login_page") {
            if (call.sessions.get<UserSession>() != null) {
                call.respondRedirect("/")
                return@get
            }

            val hasError = call.request.queryParameters["error"] == "invalid"
            call.respond(
                PebbleContent(
                    "login_page.html",
                    mapOf("error" to hasError)
                )
            )
        }

        get("/signup_page") {
            if (call.sessions.get<UserSession>() != null) {
                call.respondRedirect("/")
                return@get
            }

            val errorMessage = when (call.request.queryParameters["error"]) {
                "empty" -> "Please enter a username and password."
                "mismatch" -> "Passwords do not match."
                "exists" -> "That username is already taken."
                "toolong" -> "Username or password is too long."
                else -> null
            }

            call.respond(
                PebbleContent(
                    "signup_page.html",
                    mapOf(
                        "hasError" to (errorMessage != null),
                        "error" to (errorMessage ?: "")
                    )
                )
            )
        }

        authenticate("auth-form") {
            post("/login") {
                val principal = call.principal<UserIdPrincipal>()
                if (principal == null) {
                    call.respondRedirect("/login_page?error=invalid")
                    return@post
                }

                call.sessions.set(UserSession(principal.name))
                call.respondRedirect("/")
            }
        }

        post("/signup") {
            val form = call.receiveParameters()
            val username = form["username"]?.trim().orEmpty()
            val password = form["password"].orEmpty()
            val confirmPassword = form["confirm_password"].orEmpty()

            if (username.isBlank() || password.isBlank()) {
                call.respondRedirect("/signup_page?error=empty")
                return@post
            }

            if (password != confirmPassword) {
                call.respondRedirect("/signup_page?error=mismatch")
                return@post
            }

            if (username.length > 50 || password.length > 100) {
                call.respondRedirect("/signup_page?error=toolong")
                return@post
            }

            val existing = userService.findUserByUsername(username)
            if (existing != null) {
                call.respondRedirect("/signup_page?error=exists")
                return@post
            }

            userService.create(ExposedUser(username = username, password = password))
            call.sessions.set(UserSession(username))
            call.respondRedirect("/")
        }

        authenticate("auth-session") {
            get("/logout_page") {
                call.respond(PebbleContent("logout_page.html", emptyMap<String, Any>()))
            }

            post("/logout") {
                call.sessions.clear<UserSession>()
                call.respondRedirect("/login_page")
            }
        }

    }

}
