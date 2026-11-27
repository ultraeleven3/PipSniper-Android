package com.apexquant.pipsniper.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs

enum class TradeLedger(val key: String, val label: String) {
    MANUAL("MANUAL", "My MT5 Journal"),
    AI_SCAN("AI_SCAN", "AI Chart Scans"),
    BOT_AUTO("BOT_AUTO", "Auto Hunter Bot");

    companion object {
        fun fromKey(key: String?): TradeLedger =
            values().firstOrNull { it.key == key } ?: AI_SCAN
    }
}

data class TradeRecord(
    val id: String = UUID.randomUUID().toString(),
    val ledger: TradeLedger = TradeLedger.AI_SCAN,
    val symbol: String = "XAUUSDm",
    val direction: String,
    val entry: Double,
    val sl: Double,
    val tp: Double,
    val lotSize: Double = 0.01,
    val pnlUsd: Double? = null,
    val status: String = "SCANNING",
    val session: String = detectPktSession(System.currentTimeMillis()),
    val strategyTag: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null
) {
    fun riskPips(): Double = abs(entry - sl) * 10.0

    fun rewardPips(): Double = abs(tp - entry) * 10.0

    fun plannedRR(): Double {
        val risk = abs(entry - sl)
        return if (risk > 0.0001) abs(tp - entry) / risk else 0.0
    }

    fun realizedPips(): Double = when (status) {
        "TP_HIT" -> rewardPips()
        "SL_HIT" -> -riskPips()
        else -> 0.0
    }

    fun realizedR(): Double = when (status) {
        "TP_HIT" -> plannedRR()
        "SL_HIT" -> -1.0
        else -> 0.0
    }

    fun effectiveTimestamp(): Long = closedAt ?: createdAt

    fun dayKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(effectiveTimestamp()))
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("ledger", ledger.key)
        put("symbol", symbol)
        put("direction", direction)
        put("entry", entry)
        put("sl", sl)
        put("tp", tp)
        put("lotSize", lotSize)
        if (pnlUsd != null) put("pnlUsd", pnlUsd)
        put("status", status)
        put("session", session)
        put("strategyTag", strategyTag)
        put("notes", notes)
        put("createdAt", createdAt)
        if (closedAt != null) put("closedAt", closedAt)
    }

    companion object {
        fun detectPktSession(timestampMs: Long): String {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Karachi"))
            cal.timeInMillis = timestampMs
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 5..11 -> "Asian"
                in 12..16 -> "London"
                in 17..22 -> "New York"
                else -> "Late NY"
            }
        }

        fun fromJson(obj: JSONObject): TradeRecord {
            val ledgerKey = if (obj.has("ledger")) {
                obj.optString("ledger")
            } else {
                when {
                    obj.optString("source").contains("Auto", ignoreCase = true) -> TradeLedger.BOT_AUTO.key
                    obj.optString("source").contains("Manual Journal", ignoreCase = true) -> TradeLedger.MANUAL.key
                    else -> TradeLedger.AI_SCAN.key
                }
            }
            return TradeRecord(
                id = obj.optString("id", UUID.randomUUID().toString()),
                ledger = TradeLedger.fromKey(ledgerKey),
                symbol = obj.optString("symbol", "XAUUSDm"),
                direction = obj.optString("direction", "BUY"),
                entry = obj.optDouble("entry", 0.0),
                sl = obj.optDouble("sl", 0.0),
                tp = obj.optDouble("tp", 0.0),
                lotSize = obj.optDouble("lotSize", 0.01),
                pnlUsd = if (obj.has("pnlUsd") && !obj.isNull("pnlUsd")) obj.optDouble("pnlUsd") else null,
                status = obj.optString("status", "SCANNING"),
                session = obj.optString("session", detectPktSession(obj.optLong("createdAt", System.currentTimeMillis()))),
                strategyTag = obj.optString("strategyTag", obj.optString("closeReason", "")),
                notes = obj.optString("notes", ""),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                closedAt = if (obj.has("closedAt") && !obj.isNull("closedAt")) obj.optLong("closedAt") else null
            )
        }
    }
}

object TradeHistoryManager {
    private const val FILE_NAME = "trade_history_db.json"
    private val lock = Any()

    private fun getDbFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun getAllTrades(context: Context): List<TradeRecord> = synchronized(lock) {
        val file = getDbFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val list = ArrayList<TradeRecord>(arr.length())
            for (i in 0 until arr.length()) {
                list.add(TradeRecord.fromJson(arr.getJSONObject(i)))
            }
            list.sortedByDescending { it.effectiveTimestamp() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAll(context: Context, trades: List<TradeRecord>) = synchronized(lock) {
        val arr = JSONArray()
        trades.forEach { arr.put(it.toJson()) }
        getDbFile(context).writeText(arr.toString(2))
    }

fun recordNewTrade(
        context: Context,
        source: String,
        direction: String,
        entry: Double,
        sl: Double,
        tp: Double,
        initialStatus: String = "SCANNING",
        initialReason: String = ""
    ): String {
        val targetLedger = when {
            source.contains("Auto", ignoreCase = true) || source.contains("Bot", ignoreCase = true) -> TradeLedger.BOT_AUTO
            source.contains("Journal", ignoreCase = true) -> TradeLedger.MANUAL
            else -> TradeLedger.AI_SCAN
        }
        val record = TradeRecord(
            ledger = targetLedger,
            direction = direction,
            entry = entry,
            sl = sl,
            tp = tp,
            status = initialStatus,
            strategyTag = if (initialReason.isNotBlank()) initialReason else source
        )
        addTrade(context, record)
        return record.id
    }

    fun updateTradeStatus(
        context: Context,
        tradeId: String,
        newStatus: String,
        reason: String? = null
    ) {
        updateStatus(context, tradeId, newStatus, reason)
    }

    fun addTrade(context: Context, record: TradeRecord) {
        val current = getAllTrades(context).toMutableList()
        current.add(0, record)
        saveAll(context, current)
    }

    fun updateTrade(context: Context, updated: TradeRecord) {
        val current = getAllTrades(context).map { if (it.id == updated.id) updated else it }
        saveAll(context, current)
    }

    fun updateStatus(context: Context, tradeId: String, newStatus: String, reason: String? = null) {
        val current = getAllTrades(context).map { record ->
            if (record.id == tradeId) {
                val isTerminal = newStatus in listOf("TP_HIT", "SL_HIT", "BE", "INVALID")
                record.copy(
                    status = newStatus,
                    strategyTag = reason ?: record.strategyTag,
                    closedAt = if (isTerminal) (record.closedAt ?: System.currentTimeMillis()) else record.closedAt
                )
            } else record
        }
        saveAll(context, current)
    }

    fun deleteTrade(context: Context, tradeId: String) {
        val current = getAllTrades(context).filterNot { it.id == tradeId }
        saveAll(context, current)
    }

    fun promoteToManualJournal(context: Context, sourceRecord: TradeRecord) {
        val cloned = sourceRecord.copy(
            id = UUID.randomUUID().toString(),
            ledger = TradeLedger.MANUAL,
            strategyTag = "Promoted from ${sourceRecord.ledger.label}",
            createdAt = System.currentTimeMillis(),
            closedAt = if (sourceRecord.status in listOf("TP_HIT", "SL_HIT", "BE")) System.currentTimeMillis() else null
        )
        addTrade(context, cloned)
    }

    fun exportToCsv(context: Context, ledger: TradeLedger): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("Date,Ledger,Symbol,Direction,Entry,SL,TP,LotSize,Status,NetPips,NetR,PnL_USD,Session,Strategy,Notes")
        getAllTrades(context)
            .filter { it.ledger == ledger }
            .forEach { t ->
                val dateStr = sdf.format(Date(t.effectiveTimestamp()))
                val safeNotes = t.notes.replace(",", ";").replace("\n", " ")
                val safeStrat = t.strategyTag.replace(",", ";")
                sb.appendLine(
                    "$dateStr,${t.ledger.label},${t.symbol},${t.direction}," +
                    "${String.format(Locale.US, "%.2f", t.entry)}," +
                    "${String.format(Locale.US, "%.2f", t.sl)}," +
                    "${String.format(Locale.US, "%.2f", t.tp)}," +
                    "${t.lotSize},${t.status}," +
                    "${String.format(Locale.US, "%.1f", t.realizedPips())}," +
                    "${String.format(Locale.US, "%.2f", t.realizedR())}," +
                    "${t.pnlUsd ?: ""}," +
                    "${t.session},$safeStrat,$safeNotes"
                )
            }
        return sb.toString()
    }
}
