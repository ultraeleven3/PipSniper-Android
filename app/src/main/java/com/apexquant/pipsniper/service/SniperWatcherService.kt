package com.apexquant.pipsniper.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.apexquant.pipsniper.core.MarketDataEngine
import com.apexquant.pipsniper.core.PrefsManager
import com.apexquant.pipsniper.core.StrategyEngine
import com.apexquant.pipsniper.ui.MainActivity
import kotlinx.coroutines.*
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SniperWatcherService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val FG_CHANNEL = "pipsniper_terminal_fg"
    private val ALERT_CHANNEL = "pipsniper_terminal_alerts"
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastAutoCandleTs = 0L

    override fun onCreate() {
        super.onCreate()
        createChannels()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PipSniper::TerminalLock")
        startForeground(101, buildForegroundNotification("Pip Sniper Terminal initializing..."))
        startLoop()
    }

    private fun startLoop() {
        scope.launch {
            while (isActive) {
                try {
                    val prefs = PrefsManager(this@SniperWatcherService)
                    val initialSetups = prefs.getSetups()
                    val autoEnabled = prefs.autoHunterEnabled

                    if (initialSetups.length() == 0 && !autoEnabled) {
                        releaseWakeLock()
                        updateForegroundNotification("Standby | No active setups or Auto-Hunter")
                        delay(15_000)
                        continue
                    }

                    acquireWakeLock()
                    val candles = MarketDataEngine.fetchCandles(
                        symbol = prefs.symbol,
                        interval = "1m",
                        range = "1d",
                        exnessOffset = prefs.exnessOffset,
                        limit = 40
                    )

                    if (candles.size >= 10) {
                        val liveCandle = candles.last()
                        val closedCandle = candles[candles.size - 2]
                        val currentPrice = liveCandle.close
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                        // 1. Evaluate Mode 1 Manual / AI Setups
                        val freshSetups = prefs.getSetups()
                        val updated = JSONArray()
                        var nearestDist = Double.MAX_VALUE

                        for (i in 0 until freshSetups.length()) {
                            val s = freshSetups.getJSONObject(i)
                            val dir = s.getString("direction").uppercase(Locale.US)
                            val entry = s.getDouble("entry")
                            val sl = s.getDouble("sl")
                            val tp = s.getDouble("tp")
                            var tapped = s.optBoolean("tapped", false)
                            var tapTimestamp = s.optLong("tap_ts", 0L)

                            nearestDist = min(nearestDist, abs(currentPrice - entry))

                            if ((dir == "BUY" && currentPrice <= sl) || (dir == "SELL" && currentPrice >= sl)) {
                                sendAlert("Setup Invalidated ($dir)", "$dir @ $entry hit SL $sl before 1m confirmation.", urgent = false)
                                continue
                            }

                            val recentLow = min(liveCandle.low, closedCandle.low)
                            val recentHigh = max(liveCandle.high, closedCandle.high)

                            if (!tapped) {
                                val hitZone = if (dir == "BUY") {
                                    recentLow <= entry + 0.80 && currentPrice > sl
                                } else {
                                    recentHigh >= entry - 0.80 && currentPrice < sl
                                }

                                if (hitZone) {
                                    tapped = true
                                    tapTimestamp = if (
                                        (dir == "BUY" && closedCandle.low <= entry + 0.80) ||
                                        (dir == "SELL" && closedCandle.high >= entry - 0.80)
                                    ) closedCandle.timestamp else liveCandle.timestamp

                                    s.put("tapped", true)
                                    s.put("tap_ts", tapTimestamp)
                                    sendAlert("Zone Tapped! ($dir)", "Price tapped $entry. Waiting for 1m closed confirmation...", urgent = false)
                                }
                            }

                            if (tapped && closedCandle.timestamp >= tapTimestamp) {
                                if (StrategyEngine.check1mZoneConfirmation(candles, dir)) {
                                    sendAlert(
                                        "$dir CONFIRMED!",
                                        "Exness XAUUSD: ${String.format(Locale.US, "%.2f", currentPrice)} | Entry: $entry | SL: $sl | TP: $tp",
                                        urgent = true
                                    )
                                    continue
                                }
                            }
                            updated.put(s)
                        }

                        prefs.setupsJson = updated.toString()
                        prefs.lastPrice = String.format(Locale.US, "%.2f", currentPrice)

                        // 2. Evaluate Mode 2 Autonomous Strategy Hunter
                        if (autoEnabled) {
                            val signal = StrategyEngine.scanAutonomousStrategies(candles, prefs)
                            if (signal != null && signal.candleTimestamp > lastAutoCandleTs) {
                                lastAutoCandleTs = signal.candleTimestamp
                                sendAlert(
                                    "AUTO SIGNAL: ${signal.direction} (${signal.strategyName})",
                                    "Entry: ${String.format(Locale.US, "%.2f", signal.entry)} | SL: ${String.format(Locale.US, "%.2f", signal.sl)} | TP: ${String.format(Locale.US, "%.2f", signal.tp)}\n${signal.reason}",
                                    urgent = true
                                )
                            }
                        }

                        val distLabel = if (nearestDist < Double.MAX_VALUE) String.format(Locale.US, "%.2f", nearestDist) else "Auto"
                        updateForegroundNotification(
                            "XAUUSD: ${String.format(Locale.US, "%.2f", currentPrice)} | Target: $distLabel | $timeStr"
                        )
                    } else {
                        updateForegroundNotification("Feed stalled — retrying...")
                    }
                } catch (_: Exception) {
                    updateForegroundNotification("Reconnecting live feed...")
                }
                delay(15_000)
            }
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, FG_CHANNEL)
            .setContentTitle("Pip Sniper Terminal")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateForegroundNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(101, buildForegroundNotification(statusText))
    }

    private fun sendAlert(title: String, msg: String, urgent: Boolean) {
        val alarmSound = RingtoneManager.getDefaultUri(
            if (urgent) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
        )
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setContentTitle(title)
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 800))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(FG_CHANNEL, "Pip Sniper Terminal", NotificationManager.IMPORTANCE_LOW)
            )
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            val alertCh = NotificationChannel(ALERT_CHANNEL, "Pip Sniper Trade Signals", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 800)
                setSound(alarmUri, audioAttrs)
            }
            nm.createNotificationChannel(alertCh)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == false) wakeLock?.acquire(60 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }
}
