package net.mymai1208.bot

import kotlinx.coroutines.SupervisorJob
import net.mymai1208.bot.exchange.AbstractExchange
import net.mymai1208.bot.exchange.binance.Binance

object ExchangeManager {
    private val job = SupervisorJob()
    private val exchanges = mutableListOf<AbstractExchange>()

    fun setup() {
        exchanges.add(Binance(job))
        exchanges.forEach {
            it.connect()
        }
    }
}