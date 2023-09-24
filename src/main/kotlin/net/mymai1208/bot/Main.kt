package net.mymai1208.bot

import net.mymai1208.bot.data.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

const val IS_DEBUG = false

private val LOGGER = LoggerFactory.getLogger("Core")

suspend fun main(args: Array<String>) {
    if(!IS_DEBUG) {
        val dbName = System.getenv("BOT_DB_NAME")
        val user = System.getenv("BOT_DB_USER")
        val password = System.getenv("BOT_DB_PASSWORD")

        Database.connect("jdbc:postgresql://localhost:5432/$dbName", "org.postgresql.Driver", user, password, databaseConfig = DatabaseConfig {
            useNestedTransactions = true
        })

        newSuspendedTransaction {
            SchemaUtils.create(Cryptos, DepthUpdates, Logs)
        }

        if(newSuspendedTransaction { Cryptos.selectAll().count() } == 0L) {
            LOGGER.info("Not Found Coin Data")

            Cryptos.grabCoins()

            LOGGER.info("Complete Grab Coins")
        }

        newSuspendedTransaction {
            Logs.insert {
                it[eventType] = EventType.START_APP
                it[timestamp] = System.currentTimeMillis()
            }
        }
    }

    ExchangeManager
    ExchangeManager.setup()

    while (true) {
        val command = readlnOrNull() ?: continue
    }
}