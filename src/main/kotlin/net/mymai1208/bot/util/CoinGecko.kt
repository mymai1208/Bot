package net.mymai1208.bot.util

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

object CoinGecko {
    private val httpClient = HttpClient(Java) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun getCoins(page: Int, perPage: Int = 100): List<Pair<String, String>> {
        val json = httpClient.get("https://api.coingecko.com/api/v3/coins/markets") {
            parameter("vs_currency", "usd")
            parameter("order", "market_cap_desc")
            parameter("per_page", perPage)
            parameter("page", page)
            parameter("sparkline", false)
            parameter("locale", "en")
        }.body<JsonArray>()

        return json
            .filter { it.jsonObject["name"] != null && it.jsonObject["symbol"] != null }
            .map { it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject["symbol"]!!.jsonPrimitive.content }
    }

    suspend fun getCoin(symbol: String): Pair<String, String>? {
        try {
            val json = httpClient.get("https://api.coingecko.com/api/v3/search") {
                parameter("query", symbol)
            }.body<JsonObject>()

            val coins = json["coins"]?.jsonArray?.takeIf { it.isNotEmpty() } ?: return null

            return coins[0].jsonObject["name"]!!.jsonPrimitive.content to coins[0].jsonObject["symbol"]!!.jsonPrimitive.content.lowercase()
        } catch (ex: NoTransformationFoundException) {
            return null
        }
    }
}