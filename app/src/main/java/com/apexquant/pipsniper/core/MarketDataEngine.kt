package com.apexquant.pipsniper.core

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

object MarketDataEngine {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetchCandles(
        symbol: String = "XAUUSDm",
        interval: String = "1m",
        range: String = "1d",
        limit: Int = 50
    ): List<Candle> {
        // Primary: Binance Public Klines for PAXGUSDT (1:1 Physical Gold Spot XAU/USD, zero API key, 1m OHLC)
        val binanceEndpoints = listOf(
            "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=$interval&limit=$limit",
            "https://data-api.binance.vision/api/v3/klines?symbol=PAXGUSDT&interval=$interval&limit=$limit"
        )

        for (url in binanceEndpoints) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()

                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use
                    val body = res.body?.string() ?: return@use
                    val arr = JSONArray(body)
                    val list = mutableListOf<Candle>()
                    for (i in 0 until arr.length()) {
                        val k = arr.getJSONArray(i)
                        list.add(
                            Candle(
                                timestamp = k.getLong(0) / 1000L,
                                open = k.getString(1).toDouble(),
                                high = k.getString(2).toDouble(),
                                low = k.getString(3).toDouble(),
                                close = k.getString(4).toDouble()
                            )
                        )
                    }
                    if (list.isNotEmpty()) return list
                }
            } catch (_: Exception) {
            }
        }

        // Secondary: Bybit Public Klines for PAXGUSDT
        try {
            val bybitInterval = when (interval) {
                "1m" -> "1"
                "5m" -> "5"
                "15m" -> "15"
                "1h" -> "60"
                else -> "1"
            }
            val bybitUrl = "https://api.bybit.com/v5/market/kline?category=spot&symbol=PAXGUSDT&interval=$bybitInterval&limit=$limit"
            val req = Request.Builder().url(bybitUrl).build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: ""
                    val listArr = JSONObject(body)
                        .getJSONObject("result")
                        .getJSONArray("list")
                    val list = mutableListOf<Candle>()
                    // Bybit returns newest first, so reverse
                    for (i in listArr.length() - 1 downTo 0) {
                        val k = listArr.getJSONArray(i)
                        list.add(
                            Candle(
                                timestamp = k.getString(0).toLong() / 1000L,
                                open = k.getString(1).toDouble(),
                                high = k.getString(2).toDouble(),
                                low = k.getString(3).toDouble(),
                                close = k.getString(4).toDouble()
                            )
                        )
                    }
                    if (list.isNotEmpty()) return list
                }
            }
        } catch (_: Exception) {
        }

        // Tertiary: Gold-API Live XAU Spot fallback
        try {
            val req = Request.Builder().url("https://api.gold-api.com/price/XAU").build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val price = JSONObject(res.body?.string() ?: "{}").optDouble("price", 0.0)
                    if (price > 0.0) {
                        val now = System.currentTimeMillis() / 1000L
                        return (0 until 20).map { idx ->
                            Candle(now - (20 - idx) * 60L, price, price, price, price)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        return emptyList()
    }
}
