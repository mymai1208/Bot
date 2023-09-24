package net.mymai1208.bot.data

import kotlinx.coroutines.delay
import net.mymai1208.bot.util.CoinGecko
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object Cryptos: LongIdTable() {
    val name = varchar("name", 50)
    val symbol = varchar("symbol", 50)
}

suspend fun Cryptos.grabCoins() {
    repeat(5) {
        CoinGecko.getCoins(it + 1).forEach { pair ->
            newSuspendedTransaction {
                val count = select { name eq pair.first and (symbol eq pair.second) }.count()

                if(count == 0L) {
                    insert {
                        it[name] = pair.first
                        it[symbol] = pair.second
                    }
                }
            }
        }

        println("completed ${it + 1} Page")
        println("wait 60sec")

        delay(60000)
    }
}

suspend fun Cryptos.grabCoin(symbol: String): Boolean {
    val coin = CoinGecko.getCoin(symbol) ?: return false

    return newSuspendedTransaction {
        val count = select { name eq coin.first and (Cryptos.symbol eq symbol.lowercase()) }.count()

        if(count == 0L) {
            insert {
                it[name] = coin.first
                it[Cryptos.symbol] = coin.second
            }

            return@newSuspendedTransaction true
        }

        false
    }
}