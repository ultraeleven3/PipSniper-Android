package com.apexquant.pipsniper.core

import android.content.Context

data class TradingGoals(
    val dailyProfitTargetUsd: Double = 50.0,
    val monthlyProfitTargetUsd: Double = 1000.0,
    val dailyMaxLossUsd: Double = 30.0,
    val dailyMaxTrades: Int = 3
)

object GoalsManager {
    private const val PREFS_NAME = "pipsniper_trading_goals"
    private const val KEY_DAILY_TARGET = "daily_profit_target_usd"
    private const val KEY_MONTHLY_TARGET = "monthly_profit_target_usd"
    private const val KEY_DAILY_MAX_LOSS = "daily_max_loss_usd"
    private const val KEY_DAILY_MAX_TRADES = "daily_max_trades"

    fun getGoals(context: Context): TradingGoals {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return TradingGoals(
            dailyProfitTargetUsd = prefs.getFloat(KEY_DAILY_TARGET, 50.0f).toDouble(),
            monthlyProfitTargetUsd = prefs.getFloat(KEY_MONTHLY_TARGET, 1000.0f).toDouble(),
            dailyMaxLossUsd = prefs.getFloat(KEY_DAILY_MAX_LOSS, 30.0f).toDouble(),
            dailyMaxTrades = prefs.getInt(KEY_DAILY_MAX_TRADES, 3)
        )
    }

    fun saveGoals(context: Context, goals: TradingGoals) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_DAILY_TARGET, goals.dailyProfitTargetUsd.toFloat())
            .putFloat(KEY_MONTHLY_TARGET, goals.monthlyProfitTargetUsd.toFloat())
            .putFloat(KEY_DAILY_MAX_LOSS, goals.dailyMaxLossUsd.toFloat())
            .putInt(KEY_DAILY_MAX_TRADES, goals.dailyMaxTrades)
            .apply()
    }
}
