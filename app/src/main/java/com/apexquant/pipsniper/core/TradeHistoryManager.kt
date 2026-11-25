package com.apexquant.pipsniper.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class TradeRecord(
    val id: String,
    val timestamp: Long,
    val source: String,      // "Manual Scan" or "Auto Hunter"
    val direction: String,   // "BUY" or "SELL"
    val entry: Double,
    val sl: Double,
    val tp: Double,
    val status: String,      // "SCANNING", "TRIGGERED", "TP_HIT", "SL_HIT", "INVALID"
    val closeReason: String,
    val closedAt: Long
)

data class PeriodSummary(
    val total: Int,
    val tpHit: Int,
    val slHit: Int,
    val invalid: Int,
    val active: Int
)

object TradeHistoryManager {
    private const val FILE_NAME = "trade_history_db.json"
    private val lock = Any()

    private fun getDbFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun loadAll(context: Context): MutableList<TradeRecord> = synchronized(lock) {
        val file = getDbFile(context)
        if (!file.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<TradeRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    TradeRecord(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        source = o.optString("source", "Manual Scan"),
                        direction = o.optString("direction", "BUY"),
                        entry = o.optDouble("entry", 0.0),
                        sl = o.optDouble("sl", 0.0),
                        tp = o.optDouble("tp", 0.0),
                        status = o.optString("status", "SCANNING"),
                        closeReason = o.optString("closeReason", "Awaiting 1m confirmation"),
                        closedAt = o.optLong("closedAt", 0L)
                    )
                )
            }
            list.sortByDescending { it.timestamp }
            list
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveAll(context: Context, list: List<TradeRecord>) = synchronized(lock) {
        val arr = JSONArray()
        for (r in list) {
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("timestamp", r.timestamp)
                put("source", r.source)
                put("direction", r.direction)
                put("entry", r.entry)
                put("sl", r.sl)
                put("tp", r.tp)
                put("status", r.status)
                put("closeReason", r.closeReason)
                put("closedAt", r.closedAt)
            })
        }
        getDbFile(context).writeText(arr.toString())
    }

    fun recordNewTrade(
        context: Context,
        source: String,
        direction: String,
        entry: Double,
        sl: Double,
        tp: Double,
        initialStatus: String = "SCANNING",
        initialReason: String = "Scanning for 1m confirmation"
    ): String {
        val id = UUID.randomUUID().toString()
        val list = loadAll(context)
        list.add(
            0,
            TradeRecord(
                id = id,
                timestamp = System.currentTimeMillis(),
                source = source,
                direction = direction,
                entry = entry,
                sl = sl,
                tp = tp,
                status = initialStatus,
                closeReason = initialReason,
                closedAt = 0L
            )
        )
        saveAll(context, list)
        return id
    }

    fun updateTradeStatus(context: Context, id: String, newStatus: String, reason: String) {
        if (id.isEmpty()) return
        val list = loadAll(context)
        val idx = list.indexOfFirst { it.id == id }
        if (idx != -1) {
            val old = list[idx]
            val isTerminal = newStatus in listOf("TP_HIT", "SL_HIT", "INVALID")
            list[idx] = old.copy(
                status = newStatus,
                closeReason = reason,
                closedAt = if (isTerminal) System.currentTimeMillis() else old.closedAt
            )
            saveAll(context, list)
        }
    }

    fun computeSummaries(records: List<TradeRecord>): Triple<PeriodSummary, PeriodSummary, PeriodSummary> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000

        fun summarize(subset: List<TradeRecord>) = PeriodSummary(
            total = subset.size,
            tpHit = subset.count { it.status == "TP_HIT" },
            slHit = subset.count { it.status == "SL_HIT" },
            invalid = subset.count { it.status == "INVALID" },
            active = subset.count { it.status == "SCANNING" || it.status == "TRIGGERED" }
        )

        return Triple(
            summarize(records.filter { it.timestamp >= startOfDay }),
            summarize(records.filter { it.timestamp >= sevenDaysAgo }),
            summarize(records.filter { it.timestamp >= thirtyDaysAgo })
        )
    }

    fun formatTimestamp(ts: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.US)
        return sdf.format(Date(ts))
    }
}
