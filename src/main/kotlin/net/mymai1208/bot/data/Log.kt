package net.mymai1208.bot.data

import org.jetbrains.exposed.dao.id.LongIdTable

object Logs : LongIdTable() {
    val eventType = enumeration<EventType>("event")
    val message = varchar("message", 128).nullable()
    val exchange = enumeration<Exchanges>("exchange").nullable()
    val cryptoId = reference("crypto_id", Cryptos).nullable()
    val timestamp = long("timestamp")
}