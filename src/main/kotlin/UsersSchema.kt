package com.library

import database.UserRoles
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.*

@Serializable
data class ExposedUser(
    val username: String,
    val password: String,
)

class UserService(
    database: Database,
) 
{
    object Users : Table("users") {
        val id = integer("id").autoIncrement()
        val username = varchar("username", length = 255).uniqueIndex()
        val role = enumerationByName("role", 255, UserRoles::class).default(UserRoles.CUSTOMER)
        val password = varchar("password", length = 255)
        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users)
        }
    }

    suspend fun verifyPassword(
        username: String,
        password: String,
    ): Boolean =
        dbQuery {
            Users
                .selectAll()
                .where { (Users.username eq username) and (Users.password eq password) }
                .limit(1)
                .count() == 1L
        }

    suspend fun findUserByUsername(username: String): ExposedUser? {
        return dbQuery {
            Users
                .selectAll()
                .where { Users.username eq username }
                .map { ExposedUser(it[Users.username], it[Users.password]) }
                .singleOrNull()
        }
    }

    suspend fun create(user: ExposedUser): Int =
        dbQuery {
            Users.insert {
                it[username] = user.username
                it[role] = UserRoles.CUSTOMER
                it[password] = user.password
            }[Users.id]
        }

    suspend fun read(id: Int): ExposedUser? =
        dbQuery {
            Users
                .selectAll()
                .where { Users.id eq id }
                .map { ExposedUser(it[Users.username], it[Users.password]) }
                .singleOrNull()
        }

    suspend fun update(
        id: Int,
        user: ExposedUser,
    ) {
        dbQuery {
            Users.update({ Users.id eq id }) {
                it[username] = user.username
                it[password] = user.password
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            Users.deleteWhere { Users.id.eq(id) }
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }
}
