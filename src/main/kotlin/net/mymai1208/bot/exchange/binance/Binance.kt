package net.mymai1208.bot.exchange.binance

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import net.mymai1208.bot.data.EventType
import net.mymai1208.bot.data.Exchanges
import net.mymai1208.bot.data.Logs
import net.mymai1208.bot.exchange.AbstractExchange
import net.mymai1208.bot.exchange.AbstractProcessor
import net.mymai1208.bot.util.DatabaseUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class Binance(job: Job) : AbstractExchange(job) {
    companion object {
        val LOGGER = LoggerFactory.getLogger("Binance")
    }

    private var sequence = AtomicInteger()

    private val processors = mutableMapOf<String, AbstractProcessor>()
    private val checkingPairs = mutableSetOf<String>()
    private val mutex = Mutex()

    private var receiverJob: Job? = null

    private val tokenRegex = "(.*?)(USDT)\$".toRegex()

    override val ENDPOINT: String = "wss://stream.binance.com/ws"

    override suspend fun onConnect() {
        sequence = AtomicInteger()

        val flow = recvQueue!!.receiveAsFlow()

        receiverJob = flow
            .filter { it is JsonArray }
            .buffer(Channel.CONFLATED)
            .onEach {
                (it as JsonArray).forEach {
                    val eventName = it.jsonObject["e"]?.jsonPrimitive?.content ?: return@forEach

                    if(eventName == "24hrMiniTicker") {
                        val pair = it.jsonObject["s"]?.jsonPrimitive?.content ?: return@forEach

                        if(!tokenRegex.containsMatchIn(pair)) {
                            return@forEach
                        }

                        val isSubscribed = subscribeDepth(pair)

                        if(isSubscribed) {
                            val dbId = DatabaseUtils.getId(tokenRegex, pair)!!

                            newSuspendedTransaction {
                                Logs.insert {
                                    it[eventType] = EventType.SUBSCRIBE_PAIR
                                    it[exchange] = Exchanges.Binance
                                    it[cryptoId] = dbId
                                    it[timestamp] = System.currentTimeMillis()
                                }
                            }

                            val processor = BinanceProcessor(
                                pair,
                                job,
                                flow
                                    .mapNotNull { it as? JsonObject }
                                    .filter { it["s"]?.jsonPrimitive?.content == pair }
                                    .buffer(Channel.UNLIMITED)
                            )

                            processors[pair] = processor

                            processor.create()
                        }
                    }
                }
            }
            .launchIn(this)

        subscribe("!miniTicker@arr")
    }

    override suspend fun onClose(isForce: Boolean) {
        if(isForce) {
            receiverJob?.cancel()
        }

        processors.forEach { it.value.stop() }
        processors.clear()

        checkingPairs.clear()
    }

    override suspend fun isResponse(jsonElement: JsonElement): Boolean {
        if(jsonElement !is JsonObject) {
            return false
        }

        return jsonElement.jsonObject["id"]?.jsonPrimitive?.intOrNull != null
    }

    override suspend fun waitResponse(request: JsonElement): JsonElement? {
        repeat(10) {
            val json = responseQueue!!.receive().jsonObject

            val responseId = json["id"]?.jsonPrimitive?.int

            if(request.jsonObject["id"]?.jsonPrimitive?.int == responseId) {
                return json
            }

            delay(1000)
        }

        return null
    }

    override suspend fun send(jsonElement: JsonElement): JsonElement? {
        if(jsonElement is JsonObject) {
            val ownSeq = sequence.incrementAndGet()

            return super.send(JsonObject(jsonElement.toMutableMap().apply {
                put("id", JsonPrimitive(ownSeq))
            }))
        }

        return super.send(jsonElement)
    }

    private suspend fun getSubscribes(): List<String> {
        val result = send {
            put("method", "LIST_SUBSCRIPTIONS")
        }

        if(result !is JsonObject) {
            return emptyList()
        }

        return result["result"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    }

    private suspend fun subscribe(vararg events: String): Boolean {
        val result = send {
            put("method", "SUBSCRIBE")
            putJsonArray("params") {
                events.forEach { add(it) }
            }
        } ?: return false

        LOGGER.debug("subscribed ${events.joinToString(",")}")

        return result.jsonObject["result"] is JsonNull
    }

    private suspend fun unsubscribe(vararg events: String): Boolean {
        val result = send {
            put("method", "UNSUBSCRIBE")
            putJsonArray("params") {
                events.forEach { add(it) }
            }
        } ?: return false

        LOGGER.debug("unsubscribed ${events.joinToString(",")}")

        return result.jsonObject["result"] is JsonNull
    }

    private suspend fun subscribeDepth(pair: String): Boolean = mutex.withLock {
        if (checkingPairs.contains(pair)) {
            return@withLock false
        }

        if(DatabaseUtils.getId(tokenRegex, pair) == null) {
            return@withLock false
        }

        val isSuccess = subscribe("${pair.lowercase()}@depth")

        if(isSuccess) {
            checkingPairs.add(pair)
        }

        return@withLock isSuccess
    }
}