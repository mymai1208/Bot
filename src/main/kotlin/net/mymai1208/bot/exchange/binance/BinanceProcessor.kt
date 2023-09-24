package net.mymai1208.bot.exchange.binance

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import net.mymai1208.bot.data.Exchanges
import net.mymai1208.bot.data.TradeType
import net.mymai1208.bot.exchange.AbstractProcessor
import org.slf4j.Logger

class BinanceProcessor(private val pair: String, parent: Job, private val flow: Flow<JsonObject>) : AbstractProcessor(pair, parent) {
    override val tokenRegex = "(.*?)(USDT|USD)\$".toRegex()
    override val LOGGER: Logger = Binance.LOGGER
    override val exchange: Exchanges = Exchanges.Binance

    override fun start() {
        depthUpdate()
    }

    private fun depthUpdate() = flow
        .filter { it["e"]?.jsonPrimitive?.content == "depthUpdate" }
        .onEach {
            val bidOrders = it["b"]?.jsonArray
            val askOrders = it["a"]?.jsonArray

            launch {
                bidOrders?.forEach {
                    val price = it.jsonArray[0].jsonPrimitive.content
                    val amount = it.jsonArray[1].jsonPrimitive.double

                    updateDepth(price, amount, TradeType.BID)
                }
            }

            launch {
                askOrders?.forEach {
                    val price = it.jsonArray[0].jsonPrimitive.content
                    val amount = it.jsonArray[1].jsonPrimitive.double

                    updateDepth(price, amount, TradeType.ASK)
                }
            }
        }
        .onCompletion { println("completed $pair") }
        .launchIn(this)
}