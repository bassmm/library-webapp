package com.library.database

import database.Books
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File

object DatabaseSeeder {

    fun seedBooksFromCsv(csvFilePath: String) {
        val file = File(csvFilePath)

        if (!file.exists()) {
            println("Seeding skipped: CSV file not found at ${file.absolutePath}")
            return
        }

        transaction {
            // Check if the database is already seeded to avoid duplicates on restart
            if (Books.selectAll().count() > 0) {
                println("Database already contains book data. Skipping seed.")
                return@transaction
            }

            println("Seeding database from CSV...")

            // Regex: splits on commas EXCEPT when they are inside double quotes
            val csvSplitRegex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()

            file.useLines { lines ->
                // .drop(1) safely skips the header row ("title,author,isbn_13...")
                lines.drop(1).forEach { line ->

                    // Split the line, then trim whitespace and remove the surrounding double quotes
                    val tokens = line.split(csvSplitRegex).map { it.trim().removeSurrounding("\"") }

                    // Make sure we have at least Title and Author before inserting
                    if (tokens.size >= 2) {

                        Books.insert {
                            it[title] = tokens[0]
                            it[author] = tokens[1]
                            it[isbn13] = tokens.getOrNull(2)?.takeIf { it.isNotBlank() }
                            it[formatCode] = tokens.getOrNull(3)?.takeIf { s -> s.isNotBlank() }
                            it[locationCode] = tokens.getOrNull(4)?.takeIf { s -> s.isNotBlank() }
                            it[notes] = tokens.getOrNull(5)?.takeIf { s -> s.isNotBlank() }

                            // Default all new books to available
                            it[available] = true
                        }
                    }
                }
            }
            println("Database seeding complete! Loaded ${Books.selectAll().count()} books.")
        }
    }
}
