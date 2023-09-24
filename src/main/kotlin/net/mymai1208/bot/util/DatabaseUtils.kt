package net.mymai1208.bot.util

import net.mymai1208.bot.data.Cryptos
import net.mymai1208.bot.data.grabCoin
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object DatabaseUtils {
    suspend fun getId(tokenRegex: Regex, pair: String): EntityID<Long>? {
        val symbol = tokenRegex.find(pair)?.groupValues?.get(1) ?: return null

        val id = newSuspendedTransaction {
            Cryptos.select { Cryptos.symbol eq symbol.lowercase() }.firstOrNull()
        }

        if(id != null) {
            return id[Cryptos.id]
        }

        if (!Cryptos.grabCoin(symbol)) {
            return null
        }

        return newSuspendedTransaction {
            Cryptos.select { Cryptos.symbol eq symbol.lowercase() }.firstOrNull()
        }?.get(Cryptos.id)
    }
}