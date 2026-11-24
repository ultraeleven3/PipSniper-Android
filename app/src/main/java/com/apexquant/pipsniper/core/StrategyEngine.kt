package com.apexquant.pipsniper.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class AutoSignal(
    val strategyName: String,
    val direction: String,
    val entry: Double,
    val sl: Double,
    val tp: Double,
    val reason: String,
    val candleTimestamp: Long
)

object StrategyEngine {

    /** Mode 1: Confirms manual/AI-scanned entry zones on closed 1m candles */
    fun check1mZoneConfirmation(candles: List<Candle>, dir: String): Boolean {
        if (candles.size < 7) return false
        val c1 = candles[candles.size - 2] // Last closed 1m candle
        val c2 = candles[candles.size - 3]
        val prevSlice = candles.subList(candles.size - 6, candles.size - 2)
        val recentHigh = prevSlice.maxOf { it.high }
        val recentLow = prevSlice.minOf { it.low }

        val body = abs(c1.close - c1.open)
        val upperWick = c1.high - max(c1.close, c1.open)
        val lowerWick = min(c1.close, c1.open) - c1.low

        return if (dir == "BUY") {
            val choch = c1.close > recentHigh && c1.close > c1.open
            val pinbar = lowerWick >= 1.8 * max(body, 0.05) && c1.close >= c1.open
            val engulfing = c1.close > c1.open && c2.close < c2.open && c1.close > c2.open
            choch || pinbar || engulfing
        } else {
            val choch = c1.close < recentLow && c1.close < c1.open
            val pinbar = upperWick >= 1.8 * max(body, 0.05) && c1.close <= c1.open
            val engulfing = c1.close < c1.open && c2.close > c2.open && c1.close < c2.open
            choch || pinbar || engulfing
        }
    }

    /** Mode 2: Autonomous Multi-Strategy Scanner */
    fun scanAutonomousStrategies(candles1m: List<Candle>, prefs: PrefsManager): AutoSignal? {
        if (candles1m.size < 20) return null
        val c1 = candles1m[candles1m.size - 2] // Closed 1m candle

        // Strategy A: Liquidity Sweep + Rejection Reversal
        if (prefs.stratLiquiditySweep) {
            val lookback = candles1m.subList(candles1m.size - 17, candles1m.size - 2)
            val swingLow = lookback.minOf { it.low }
            val swingHigh = lookback.maxOf { it.high }

            if (c1.low < swingLow && c1.close > swingLow && c1.close > c1.open) {
                val sl = c1.low - 1.20
                val risk = max(c1.close - sl, 1.50)
                return AutoSignal(
                    strategyName = "SMC Liquidity Sweep",
                    direction = "BUY",
                    entry = c1.close,
                    sl = sl,
                    tp = c1.close + (risk * 2.5),
                    reason = "Swept 15m swing low ${String.format("%.2f", swingLow)} & reclaimed bullish",
                    candleTimestamp = c1.timestamp
                )
            }
            if (c1.high > swingHigh && c1.close < swingHigh && c1.close < c1.open) {
                val sl = c1.high + 1.20
                val risk = max(sl - c1.close, 1.50)
                return AutoSignal(
                    strategyName = "SMC Liquidity Sweep",
                    direction = "SELL",
                    entry = c1.close,
                    sl = sl,
                    tp = c1.close - (risk * 2.5),
                    reason = "Swept 15m swing high ${String.format("%.2f", swingHigh)} & rejected bearish",
                    candleTimestamp = c1.timestamp
                )
            }
        }

        // Strategy B: Fair Value Gap (FVG) Imbalance Tap
        if (prefs.stratFvgTap && candles1m.size >= 6) {
            val a = candles1m[candles1m.size - 5]
            val c = candles1m[candles1m.size - 3]
            // Bullish FVG: candle C low > candle A high
            if (c.low > a.high + 0.60 && c1.low <= c.low && c1.close > c.low) {
                val sl = a.high - 1.00
                val risk = max(c1.close - sl, 1.50)
                return AutoSignal(
                    strategyName = "FVG Imbalance Tap",
                    direction = "BUY",
                    entry = c1.close,
                    sl = sl,
                    tp = c1.close + (risk * 2.2),
                    reason = "Tapped bullish FVG zone & held above imbalance",
                    candleTimestamp = c1.timestamp
                )
            }
            // Bearish FVG: candle C high < candle A low
            if (c.high < a.low - 0.60 && c1.high >= c.high && c1.close < c.high) {
                val sl = a.low + 1.00
                val risk = max(sl - c1.close, 1.50)
                return AutoSignal(
                    strategyName = "FVG Imbalance Tap",
                    direction = "SELL",
                    entry = c1.close,
                    sl = sl,
                    tp = c1.close - (risk * 2.2),
                    reason = "Tapped bearish FVG zone & rejected downward",
                    candleTimestamp = c1.timestamp
                )
            }
        }

        // Strategy C: Momentum Range Breakout
        if (prefs.stratBreakoutMomentum) {
            val box = candles1m.subList(candles1m.size - 12, candles1m.size - 2)
            val boxHigh = box.maxOf { it.high }
            val boxLow = box.minOf { it.low }
            val body = abs(c1.close - c1.open)

            if (c1.close > boxHigh && body >= 1.20) {
                val sl = boxLow
                val risk = max(c1.close - sl, 1.50)
                return AutoSignal(
                    strategyName = "Momentum Breakout",
                    direction = "BUY",
                    entry = c1.close,
                    sl = sl,
                    tp = c1.close + (risk * 2.0),
                    reason = "Strong bullish displacement broke range high ${String.format("%.2f", boxHigh)}",
                    candleTimestamp = c1.timestamp
                )
            }
            if (c1.close < boxLow && body >= 1.20) {
                val sl = boxHigh
                val risk = max(sl - c1.close, 1.50)
                return AutoSignal(
                    strategyName = "Momentum Breakout",
                    direction = "SELL",
                    entry = c1.close,
                    sl = sl,
                    tp = c1.close - (risk * 2.0),
                    reason = "Strong bearish displacement broke range low ${String.format("%.2f", boxLow)}",
                    candleTimestamp = c1.timestamp
                )
            }
        }

        return null
    }
}
