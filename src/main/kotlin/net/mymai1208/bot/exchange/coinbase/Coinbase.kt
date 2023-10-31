package net.mymai1208.bot.exchange.coinbase

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import net.mymai1208.bot.exchange.AbstractExchange

class Coinbase(job: Job) : AbstractExchange(job) {
    override val ENDPOINT: String = "wss://ws-feed.exchange.coinbase.com"

    override suspend fun onConnect() {

    }

    override suspend fun onClose(isForce: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun isResponse(jsonElement: JsonElement): Boolean {
        if(jsonElement !is JsonObject) {
            return false
        }

        return jsonElement.jsonObject["type"]?.jsonPrimitive?.content == "subscriptions"
    }

    override suspend fun waitResponse(request: JsonElement): JsonElement? {
        return null
    }

    private suspend fun subscribeLevel2(pairs: List<String>): Boolean {
        send {
            put("type", "subscribe")

            putJsonArray("product_ids") {
                pairs.forEach { add(it) }
            }
            putJsonArray("channels") {
                add("level2_batch")
            }

        }

        return true
    }
}