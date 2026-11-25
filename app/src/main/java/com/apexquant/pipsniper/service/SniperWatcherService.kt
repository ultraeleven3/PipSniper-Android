package com.apexquant.pipsniper.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.apexquant.pipsniper.MainActivity
import com.apexquant.pipsniper.core.*
import kotlinx.coroutines.*
import org.json.JSONArray
import java.util.Locale

class SniperWatcherService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: PrefsManager
    private var lastAutoSignalTs = 0L

    companion object {
        const val CHANNEL_ID = "pip_sniper_alerts"
        const val FOREGROUND_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(FOREGROUND_ID, buildNotification("Pip Sniper Active", "Scanning live ${prefs.symbol} 1m candles..."))

        scope.launch {
            while (isActive) {
                try {
                    runTick()
                } catch (_: Exception) {
                }
                delay(15_000)
            }
        }
        return START_STICKY
    }

    private fun runTick() {
        val candles = MarketDataEngine.fetchCandles(
            symbol = prefs.symbol,
            interval = "1m",
            range = "1d",
            limit = 45
        )
        if (candles.size < 5) return

        val latest = candles.last()
        val closed = candles[candles.size - 2]
        val prev = candles[candles.size - 3]
        prefs.lastPrice = String.format(Locale.US, "%.2f", latest.close)
        val nowSec = System.currentTimeMillis() / 1000L

        // 1. Evaluate Armed Setups + Lifecycle Tracking for History
        val setups = prefs.getSetups()
        val remaining = JSONArray()
        for (i in 0 until setups.length()) {
            val s = setups.getJSONObject(i)
            val dir = s.getString("direction")
            val entry = s.getDouble("entry")
            val sl = s.getDouble("sl")
            val tp = s.getDouble("tp")
            val historyId = s.optString("history_id", "")
            var tapped = s.optBoolean("tapped", false)
            var tapTs = s.optLong("tap_ts", 0L)
            var triggered = s.optBoolean("triggered", false)
            val createdTs = s.optLong("created_ts", nowSec)

            if (!triggered) {
                // Check if SL was invalidated BEFORE confirmation ever occurred
                val invalidatedBySl = if (dir == "BUY") latest.low <= sl else latest.high >= sl
                val expiredWithoutConf = (nowSec - createdTs) > (4 * 3600)

                if (invalidatedBySl) {
                    TradeHistoryManager.updateTradeStatus(
                        this,
                        historyId,
                        "INVALID",
                        "Price hit SL ($sl) before 1m confirmation formed"
                    )
                    continue
                }
                if (expiredWithoutConf) {
                    TradeHistoryManager.updateTradeStatus(
                        this,
                        historyId,
                        "INVALID",
                        "Expired after 4h with no 1m confirmation"
                    )
                    continue
                }

                // Check Entry Zone Tap
                if (!tapped) {
                    val isTap = if (dir == "BUY") {
                        latest.low <= entry + 1.0 && latest.close >= sl
                    } else {
                        latest.high >= entry - 1.0 && latest.close <= sl
                    }
                    if (isTap) {
                        tapped = true
                        tapTs = nowSec
                        s.put("tapped", true)
                        s.put("tap_ts", tapTs)
                        TradeHistoryManager.updateTradeStatus(
                            this,
                            historyId,
                            "SCANNING",
                            "Entry zone tapped — awaiting 1m confirmation candle"
                        )
                    }
                }

                // Check 1m Confirmation
                if (tapped && closed.timestamp >= tapTs - 60) {
                    val pattern = StrategyEngine.detectConfirmation(dir, prev, closed)
                    if (pattern != null) {
                        triggered = true
                        s.put("triggered", true)
                        TradeHistoryManager.updateTradeStatus(
                            this,
                            historyId,
                            "TRIGGERED",
                            "Confirmed via $pattern • Now tracking TP ($tp) / SL ($sl)"
                        )
                        fireHighPriorityAlert(
                            "SNIPER $dir CONFIRMED • ${prefs.symbol}",
                            "$pattern @ ${closed.close} | Entry: $entry | SL: $sl | TP: $tp"
                        )
                    }
                }
                remaining.put(s)
            } else {
                // Trade is LIVE/TRIGGERED — check if TP or SL is reached
                val hitTp = if (dir == "BUY") latest.high >= tp else latest.low <= tp
                val hitSl = if (dir == "BUY") latest.low <= sl else latest.high >= sl

                if (hitTp) {
                    TradeHistoryManager.updateTradeStatus(
                        this,
                        historyId,
                        "TP_HIT",
                        "Target reached at $tp (${prefs.symbol})"
                    )
                    fireHighPriorityAlert(
                        "TP HIT ✅ • $dir ${prefs.symbol}",
                        "Take Profit $tp reached! Logged in Trade History."
                    )
                } else if (hitSl) {
                    TradeHistoryManager.updateTradeStatus(
                        this,
                        historyId,
                        "SL_HIT",
                        "Stop Loss hit at $sl (${prefs.symbol})"
                    )
                    fireHighPriorityAlert(
                        "SL HIT ❌ • $dir ${prefs.symbol}",
                        "Stop Loss $sl hit. Logged in Trade History."
                    )
                } else {
                    remaining.put(s)
                }
            }
        }
        prefs.setupsJson = remaining.toString()

        // 2. Evaluate Autonomous Strategy Hunter (Mode 2) & Log to History
        if (prefs.autoHunterEnabled) {
            val enabledStrategies = mutableSetOf<String>()
            if (prefs.stratSmc) enabledStrategies.add("SMC")
            if (prefs.stratFvg) enabledStrategies.add("FVG")
            if (prefs.stratBreakout) enabledStrategies.add("BREAKOUT")

            val signal = StrategyEngine.scanAutonomousStrategies(candles, enabledStrategies)
            if (signal != null && (closed.timestamp - lastAutoSignalTs) >= 300) {
                lastAutoSignalTs = closed.timestamp
                val autoId = TradeHistoryManager.recordNewTrade(
                    context = this,
                    source = "Auto Hunter (${signal.strategyName})",
                    direction = signal.direction,
                    entry = signal.entry,
                    sl = signal.sl,
                    tp = signal.tp,
                    initialStatus = "TRIGGERED",
                    initialReason = signal.reason
                )
                // Also watch this autonomous signal for TP/SL resolution
                val updated = prefs.getSetups()
                updated.put(org.json.JSONObject().apply {
                    put("history_id", autoId)
                    put("direction", signal.direction)
                    put("entry", signal.entry)
                    put("sl", signal.sl)
                    put("tp", signal.tp)
                    put("tapped", true)
                    put("triggered", true)
                    put("created_ts", nowSec)
                    put("tap_ts", nowSec)
                })
                prefs.setupsJson = updated.toString()

                fireHighPriorityAlert(
                    "AUTO HUNTER ${signal.direction} • ${signal.strategyName}",
                    "${signal.reason} | Entry: ${signal.entry} | SL: ${signal.sl} | TP: ${signal.tp}"
                )
            }
        }
    }

    private fun fireHighPriorityAlert(title: String, body: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify((System.currentTimeMillis() % 100000).toInt(), notif)
    }

    private fun buildNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Pip Sniper Trade Alerts", NotificationManager.IMPORTANCE_HIGH)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
