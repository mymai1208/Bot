package net.mymai1208.bot.exchange

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mymai1208.bot.data.*
import net.mymai1208.bot.util.DatabaseUtils
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.Logger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.hours

//TODO: データベースを参照して指値データの重複登録を回避させる
abstract class AbstractProcessor(private val pair: String, parent: Job) : CoroutineScope {
    private val job = Job(parent)

    override val coroutineContext: CoroutineContext by lazy { Dispatchers.Default + job }

    private val cacheMutex = Mutex()
    private val cacheRemover = Any()

    private val askCaches = mutableMapOf<String, Pair<Double, Long>>()
    private val bidCaches = mutableMapOf<String, Pair<Double, Long>>()

    private var dbId: EntityID<Long>? = null

    private val cacheExpire = 6.hours

    abstract val tokenRegex: Regex
    abstract val LOGGER: Logger
    abstract val exchange: Exchanges

    abstract fun start()

    suspend fun create() {
        val id = getId()

        if(id != null) {
            start()

            launch {
                while (isActive) {
                    delay(1.hours.inWholeMilliseconds)

                    clearCaches()

                    newSuspendedTransaction {
                        Logs.insert {
                            it[eventType] = EventType.CLEAR_CACHE
                            it[exchange] = this@AbstractProcessor.exchange
                            it[cryptoId] = id
                            it[timestamp] = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
    }

    suspend fun stop() {
        job.cancelAndJoin()
        clearCaches(true)

        LOGGER.debug("stopped processor - $pair")
    }

    private suspend fun clearCaches(isAll: Boolean = false) {
        cacheMutex.withLock(cacheRemover) {
            if(isAll) {
                askCaches.clear()
                bidCaches.clear()

                return@withLock
            }

            val currentTime = System.currentTimeMillis()

            if(askCaches.count() > 200) {
                askCaches.keys.filter { askCaches[it]!!.second <= (currentTime - cacheExpire.inWholeMilliseconds) }.forEach {
                    askCaches.remove(it)
                }
            }

            if(bidCaches.count() > 200) {
                bidCaches.keys.filter { bidCaches[it]!!.second <= (currentTime - cacheExpire.inWholeMilliseconds) }.forEach {
                    bidCaches.remove(it)
                }
            }
        }

        LOGGER.debug("removed caches - $pair")
    }

    private suspend fun getId(): EntityID<Long>? {
        if(dbId != null) {
            return dbId
        }

        val id = DatabaseUtils.getId(tokenRegex, pair)

        if(id != null) {
            dbId = id

            return id
        }

        return null
    }

    protected suspend fun updateDepth(price: String, amount: Double, tradeType: TradeType) {
        val caches = if (tradeType == TradeType.BID) bidCaches else askCaches

        //キャッシュクリア時のスレッド競合対策
        if(cacheMutex.holdsLock(cacheRemover)) {
            //waiting unlock
            cacheMutex.lock(caches)
            cacheMutex.unlock(caches)
        }

        if (!caches.containsKey(price)) {
            caches[price] = 0.0 to System.currentTimeMillis()
        }

        val cacheAmount = caches[price]!!.first

        if (cacheAmount != 0.0) {
            val changeAmount = Math.abs(amount - cacheAmount)

            LOGGER.info("${tradeType.name} pair $pair price $price amount $changeAmount")

            val id = getId()!!

            newSuspendedTransaction(Dispatchers.IO) {
                DepthUpdates.insert {
                    it[this.cryptoId] = id
                    it[this.pair] = this@AbstractProcessor.pair
                    it[this.exchange] = this@AbstractProcessor.exchange
                    it[this.tradeType] = tradeType
                    it[this.price] = price.toDouble()
                    it[this.amount] = changeAmount
                    it[time] = System.currentTimeMillis()
                }
            }
        }

        caches[price] = amount to System.currentTimeMillis()
    }
}