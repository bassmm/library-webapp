package com.library.database

import database.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File

object DatabaseCreation {
        fun init() {
            // Check if the 'data' directory exists, and create it if it doesn't
            val dataFolder = File("data")
            if (!dataFolder.exists()) {
                dataFolder.mkdirs()
            }

            // Creates a file in data directory named supermarket.db
            Database.connect("jdbc:sqlite:data/library.db", "org.sqlite.JDBC")

            // Open a transaction to execute database commands
            transaction {
                // CREATE TABLE IF NOT EXIST:
                SchemaUtils.create(Books, Users, Using)

                val hasPasswordColumn =
                    exec("PRAGMA table_info(users)") { rs ->
                        var found = false
                        while (rs.next()) {
                            if (rs.getString("name") == "password") {
                                found = true
                                break
                            }
                        }
                        found
                    } ?: false

                if (!hasPasswordColumn) {
                    exec("ALTER TABLE users ADD COLUMN password VARCHAR(255) NOT NULL DEFAULT ''")
                }
            }

            println("Connected to database & Tables Created")
        }
    }
