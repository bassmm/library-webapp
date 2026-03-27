package com.library

import database.Books
import database.Users
import database.Using
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


fun Application.configureRouting() {
    routing {
        get("/") {
            call.respond(PebbleContent("base.html", mapOf("user" to "Library User")))
        }

        get("/login_page") {
            call.respond(PebbleContent("login_page.html", mapOf("user" to "Library User")))
        }

        // authenticate("auth-form") { 
        //     post("/login") {
        //         call.respondText
        //     }
        // }

        get("/book/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val bookFromId = transaction {
                Books.selectAll().where(Books.bookId eq id).map {
                    mapOf(
                        "id" to it[Books.bookId],
                        "title" to it[Books.title],
                        "author" to it[Books.author],
                        "notes" to it[Books.notes],
                        "isbn" to it[Books.isbn13]
                    )
                }.singleOrNull()
            }
            call.respond(PebbleContent("book.html", mapOf("book" to bookFromId) as Map<String, Any>))
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
                        // Books that share an ISBN are the same title — group them.
                        // Books with no ISBN are treated as their own standalone group.
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

            call.respond(
                PebbleContent(
                    "search_results.html",
                    mapOf("groups" to groups, "query" to query)
                )
            )
        }
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
        // Perform an INNER JOIN on database.Books and database.Using
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
