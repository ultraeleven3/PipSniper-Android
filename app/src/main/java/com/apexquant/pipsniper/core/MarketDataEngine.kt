package com.apexquant.pipsniper.core

import okhttp3.OkHttpClient
import okhttp3.Request
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
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun fetchCandles(
        symbol: String = "GC=F",
        interval: String = "1m",
        range: String = "1d",
        exnessOffset: Double = 0.0,
        limit: Int = 50
    ): List<Candle> {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/\$symbol?interval=$interval&range=$range"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()

            val json = JSONObject(body)
            val result = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
            val timestamps = result.getJSONArray("timestamp")
            val quote = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)

            val opens = quote.getJSONArray("open")
            val highs = quote.getJSONArray("high")
            val lows = quote.getJSONArray("low")
            val closes = quote.getJSONArray("close")

            val list = mutableListOf<Candle>()
            for (i in 0 until closes.length()) {
                if (!closes.isNull(i) && !opens.isNull(i) && !highs.isNull(i) && !lows.isNull(i)) {
                    list.add(
                        Candle(
                            timestamp = timestamps.getLong(i),
                            open = opens.getDouble(i) + exnessOffset,
                            high = highs.getDouble(i) + exnessOffset,
                            low = lows.getDouble(i) + exnessOffset,
                            close = closes.getDouble(i) + exnessOffset
                        )
                    )
                }
            }
            return list.takeLast(limit)
        }
    }
}
