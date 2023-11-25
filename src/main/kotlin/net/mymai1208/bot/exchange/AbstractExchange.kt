package net.mymai1208.bot.exchange

import dev.gustavoavila.websocketclient.WebSocketClient
import io.ktor.client.plugins.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

abstract class AbstractExchange(parentJob: Job) : CoroutineScope {
    protected val LOGGER = LoggerFactory.getLogger(this::class.java)

    protected val job = SupervisorJob(parentJob)

    override val coroutineContext: CoroutineContext by lazy { Dispatchers.Default + job }

    abstract val ENDPOINT: String

    abstract suspend fun onConnect()
    abstract suspend fun onClose(isForce: Boolean)
    abstract suspend fun isResponse(jsonElement: JsonElement): Boolean
    abstract suspend fun waitResponse(request: JsonElement): JsonElement?

    private val sendQueue = Channel<JsonElement>(Channel.UNLIMITED)
    protected var recvQueue: Channel<JsonElement>? = null
    protected var responseQueue: Channel<JsonElement>? = null

    fun connect(isReConnect: Boolean = true) {
        recvQueue = Channel(Channel.UNLIMITED)
        responseQueue = Channel(Channel.UNLIMITED)

        val client = object : WebSocketClient(URI.create(ENDPOINT)) {
            var sendCoroutine: Job? = null

            override fun onOpen() {
                sendCoroutine = launch {
                    while (isActive) {
                        val json = sendQueue.receive()

                        send(json.toString())

                        waitRateLimit()
                    }
                }

                launch {
                    onConnect()
                }
            }

            override fun onTextReceived(message: String) {
                val json = Json.parseToJsonElement(message)

                launch {
                    if (isResponse(json)) {
                        responseQueue?.send(json)
                    }

                    recvQueue?.send(json)
                }
            }

            override fun onBinaryReceived(data: ByteArray) {

            }

            override fun onPingReceived(data: ByteArray) {

            }

            override fun onPongReceived(data: ByteArray) {

            }

            override fun onException(e: Exception) {
                LOGGER.error("exception", e)
            }

            override fun onCloseReceived(reason: Int, description: String?) {
                runBlocking {
                    onClose(false)
                    sendCoroutine?.cancelAndJoin()

                    recvQueue?.close()
                    responseQueue?.close()
                }

                LOGGER.info("closed websocket session Reason: $reason / ${description ?: "Empty"}")

                if(isReConnect) {
                    launch {
                        delay(1.minutes.inWholeMilliseconds)

                        this@AbstractExchange.connect()
                    }
                }
            }
        }

        client.setConnectTimeout(1000 * 10)
        client.setReadTimeout(1000 * 60)

        client.connect()
    }

    suspend fun send(builder: JsonObjectBuilder.() -> Unit): JsonElement? {
        return send(buildJsonObject(builder))
    }

    open suspend fun send(jsonElement: JsonElement): JsonElement? {
        sendQueue.send(jsonElement)

        return waitResponse(jsonElement)
    }

    open suspend fun waitRateLimit() {
        delay(1000)
    }
}