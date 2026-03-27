package com.library

import database.Books
import database.Users
import database.Using
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureRouting() {
    routing {
        get("/") {
            val session = call.sessions.get<UserSession>()
            call.respond(
                PebbleContent(
                    "base.html",
                    mapOf(
                        "user" to (session?.username ?: "Library User"),
                        "loggedIn" to (session != null)
                    )
                )
            )
        }

        get("/book/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val bookFromId = transaction {
                Books
                    .selectAll()
                    .where { Books.bookId eq id }
                    .map {
                        mapOf(
                            "id" to it[Books.bookId],
                            "title" to it[Books.title],
                            "author" to it[Books.author],
                            "notes" to it[Books.notes],
                            "isbn" to it[Books.isbn13]
                        )
                    }
                    .singleOrNull()
            }

            if (bookFromId == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val session = call.sessions.get<UserSession>()
            val borrowStatus = call.request.queryParameters["borrow"]
            val borrowMessage = when (borrowStatus) {
                "success" -> "Book borrowed successfully."
                "unavailable" -> "This book is currently unavailable."
                "error" -> "Could not borrow this book right now."
                else -> ""
            }
            val model: Map<String, Any> = mapOf(
                "book" to bookFromId,
                "loggedIn" to (session != null),
                "hasBorrowMessage" to borrowMessage.isNotBlank(),
                "borrowMessage" to borrowMessage,
                "borrowSuccess" to (borrowStatus == "success")
            )
            call.respond(PebbleContent("book.html", model))
        }

        get("/search") {
            val query = call.request.queryParameters["search"]?.trim().orEmpty()

            val groups: List<Map<String, Any>> = if (query.isBlank()) {
                emptyList()
            } else {
                transaction {
                    Books.selectAll()
                        .where {
                            (Books.title.lowerCase() like "%${query.lowercase()}%") or
                                (Books.author.lowerCase() like "%${query.lowercase()}%")
                        }
                        .orderBy(Books.title to SortOrder.ASC)
                        .toList()
                }
                    .groupBy { row ->
                        row[Books.isbn13]?.takeIf { it.isNotBlank() } ?: "noIsbn_${row[Books.bookId]}"
                    }
                    .map { (_, groupRows) ->
                        val first = groupRows.first()
                        val availableCopies = groupRows.count { it[Books.available] }
                        mapOf(
                            "isbn" to (first[Books.isbn13] ?: ""),
                            "title" to first[Books.title],
                            "author" to first[Books.author],
                            "totalCopies" to groupRows.size,
                            "availableCopies" to availableCopies,
                            "copies" to groupRows.map { row ->
                                mapOf(
                                    "id" to row[Books.bookId],
                                    "available" to row[Books.available],
                                    "location" to (row[Books.locationCode] ?: "Digital")
                                )
                            }
                        )
                    }
                    .sortedBy { (it["title"] as String).lowercase() }
            }

            call.respond(PebbleContent("search_results.html", mapOf("groups" to groups, "query" to query)))
        }

        authenticate("auth-session") {
            get("/my_books") {
                val principal = call.principal<UserIdPrincipal>()
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                val userId = transaction {
                    Users
                        .selectAll()
                        .where { Users.username eq principal.name }
                        .map { it[Users.userId] }
                        .singleOrNull()
                }

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "User account not found.")
                    return@get
                }

                val borrowedBooks: List<Map<String, Any>> = transaction {
                    (Using innerJoin Books)
                        .selectAll()
                        .where { Using.user eq userId }
                        .orderBy(Using.returnDate to SortOrder.ASC)
                        .map { row ->
                            mapOf(
                                "id" to row[Books.bookId],
                                "title" to row[Books.title],
                                "author" to row[Books.author],
                                "due" to row[Using.returnDate].toString(),
                                "taken" to row[Using.takeOutDate].toString()
                            )
                        }
                }

                call.respond(
                    PebbleContent(
                        "borrowed_books.html",
                        mapOf(
                            "user" to principal.name,
                            "loggedIn" to true,
                            "books" to borrowedBooks,
                            "returnStatus" to (call.request.queryParameters["return"] ?: "")
                        )
                    )
                )
            }

            post("/book/{id}/return") {
                val bookId = call.parameters["id"]?.toIntOrNull()
                if (bookId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val principal = call.principal<UserIdPrincipal>()
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val userId = transaction {
                    Users
                        .selectAll()
                        .where { Users.username eq principal.name }
                        .map { it[Users.userId] }
                        .singleOrNull()
                }

                if (userId == null) {
                    call.respondRedirect("/my_books?return=error")
                    return@post
                }

                val returned = returnBook(bookId, userId)
                if (returned) {
                    call.respondRedirect("/my_books?return=success")
                } else {
                    call.respondRedirect("/my_books?return=notfound")
                }
            }

            post("/book/{id}/borrow") {
                val bookId = call.parameters["id"]?.toIntOrNull()
                if (bookId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val principal = call.principal<UserIdPrincipal>()
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val userId = transaction {
                    Users
                        .selectAll()
                        .where { Users.username eq principal.name }
                        .map { it[Users.userId] }
                        .singleOrNull()
                }

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "User account not found.")
                    return@post
                }

                val borrowed = borrowBook(bookId, userId)
                if (borrowed) {
                    call.respondRedirect("/book/$bookId?borrow=success")
                } else {
                    call.respondRedirect("/book/$bookId?borrow=unavailable")
                }
            }
        }
    }
}

fun borrowBook(
    bookId: Int,
    userId: Int,
): Boolean {
    return transaction {
        val updatedRows = Books.update({ (Books.bookId eq bookId) and (Books.available eq true) }) {
            it[available] = false
        }

        if (updatedRows == 0) {
            return@transaction false
        }

        try {
            Using.insert {
                it[user] = userId
                it[book] = bookId
            }
        } catch (_: Exception) {
            Books.update({ Books.bookId eq bookId }) {
                it[available] = true
            }
            return@transaction false
        }

        true
    }
}

fun returnBook(
    bookId: Int,
    userId: Int,
): Boolean {
    return transaction {
        val deletedRows = Using.deleteWhere { (Using.book eq bookId) and (Using.user eq userId) }
        if (deletedRows == 0) {
            return@transaction false
        }

        Books.update({ Books.bookId eq bookId }) {
            it[available] = true
        }

        true
    }
}

/**
 * Searches for books matching the given title.
 * Uses a case-insensitive fuzzy search.
 */
fun searchBooksByTitle(searchTitle: String): List<String> {
    return transaction {
        Books.selectAll()
            .where { Books.title.lowerCase() like "%${searchTitle.lowercase()}%" }
            .map { row ->
                val title = row[Books.title]
                val author = row[Books.author]
                val available = if (row[Books.available]) "Available" else "Checked out"
                "'$title' by $author - Status: $available"
            }
    }
}

/**
 * Retrieves a list of books currently borrowed by a specific user.
 * Joins the 'database.Using' table with the 'database.Books' table.
 */
fun getBorrowedBooksForUser(searchUserId: Int): List<String> {
    return transaction {
        (Books innerJoin Using)
            .selectAll()
            .where { Using.user eq searchUserId }
            .map { row ->
                val title = row[Books.title]
                val returnDate = row[Using.returnDate]
                "Borrowed: '$title' | Due back: $returnDate"
            }
    }
}




