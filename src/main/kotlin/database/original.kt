package database
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.lowerCase

fun setupDatabase() {
    // Connect to the database - h2 in memory
    Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

    transaction {
       // Create the tables
        SchemaUtils.create(Books, Users, Using)

        // database.Using CSVREAD to populate the database.Books table from the csv
        // true is put in the 'available' column as it's missing in the CSV
        exec("""
            INSERT INTO books (title, author, isbn_13, format_code, location_code, notes, available) 
            SELECT title, author, isbn_13, format_code, location_code, notes, true 
            FROM CSVREAD('library_booklist.csv')
        """)
    }
}

/**
 * Searches for books matching the given author's name.
 * Uses a case-insensitive fuzzy search.
 */
fun searchBooksByAuthor(authorName: String): List<String> {
    return transaction {
        Books.selectAll()
            .where { Books.author.lowerCase() like "%${authorName.lowercase()}%" }
            .map { row ->
                val title = row[Books.title]
                val author = row[Books.author]
                val format = row[Books.formatCode] ?: "Unknown format"
                "'$title' by $author ($format)"
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