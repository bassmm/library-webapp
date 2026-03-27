package database

import java.time.LocalDateTime
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.*

enum class UserRoles {
    STAFF, CUSTOMER
}

// Books table
object Books : Table("books") {
    val bookId = integer("id").autoIncrement() // needed because isbn's aren't unique
    val title = varchar("title", 255)
    val author = varchar("author", 255)
    val isbn13 = varchar("isbn_13", 50).nullable()
    val formatCode = varchar("format_code", 10).nullable()
    val locationCode = varchar("location_code", 50).nullable()
    val notes = varchar("notes", 255).nullable()
    val available = bool("available").default(true)

    override val primaryKey = PrimaryKey(bookId)
}

// Users Table
object Users: Table("users") {
    val userId = integer("id").autoIncrement()
    val username = varchar("username", 255)
    val role = enumerationByName("role", 255, UserRoles::class).default(UserRoles.CUSTOMER)
    val password = varchar("password",255)

    override val primaryKey = PrimaryKey(userId)
}

// Using table
object Using: Table("using") {
    val user = integer("userId").references(Users.userId)
    val book = integer("bookId").references(Books.bookId)
    val takeOutDate = datetime("takeOutDate").defaultExpression(CurrentDateTime)
    val returnDate = datetime("returnDate").clientDefault { LocalDateTime.now().plusDays(7) }

    override val primaryKey = PrimaryKey(user, book)
}