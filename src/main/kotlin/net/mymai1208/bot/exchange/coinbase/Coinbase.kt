package net.mymai1208.bot.exchange.coinbase

import kotlinx.coroutines.Job
import kotlinx.serialization.json.JsonElement
import net.mymai1208.bot.exchange.AbstractExchange

class Coinbase(job: Job) : AbstractExchange(job) {
    override val ENDPOINT: String = "wss://ws-feed.exchange.coinbase.com"

    override suspend fun onConnect() {
        TODO("Not yet implemented")
    }

    override suspend fun onClose() {
        TODO("Not yet implemented")
    }

    override suspend fun isResponse(jsonElement: JsonElement): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun waitResponse(request: JsonElement): JsonElement? {
        TODO("Not yet implemented")
    }
}