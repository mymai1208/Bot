package net.mymai1208.bot.data

import org.jetbrains.exposed.dao.id.LongIdTable

object DepthUpdates : LongIdTable(){
    val cryptoId = reference("crypto_id", Cryptos)
    val pair = varchar("pair", 50)
    val exchange = enumeration<Exchanges>("exchange")
    val tradeType = enumeration<TradeType>("trade_type")
    val time = long("time")

    val price = double("target_price")
    val amount = double("amount")
}