package com.apexquant.pipsniper.core

import android.content.Context
import org.json.JSONArray

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("pipsniper_v1_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_api_key", value.trim()).apply()

    var symbol: String
        get() = prefs.getString("symbol", "GC=F") ?: "GC=F"
        set(value) = prefs.edit().putString("symbol", value).apply()

    var exnessOffset: Double
        get() = prefs.getFloat("exness_offset", 0f).toDouble()
        set(value) = prefs.edit().putFloat("exness_offset", value.toFloat()).apply()

    var lastPrice: String
        get() = prefs.getString("last_price", "--") ?: "--"
        set(value) = prefs.edit().putString("last_price", value).apply()

    var setupsJson: String
        get() = prefs.getString("setups", "[]") ?: "[]"
        set(value) = prefs.edit().putString("setups", value).apply()

    fun getSetups(): JSONArray = try {
        JSONArray(setupsJson)
    } catch (_: Exception) {
        JSONArray()
    }

    // Autonomous Strategy Toggles (Ready for infinite future strategies)
    var autoHunterEnabled: Boolean
        get() = prefs.getBoolean("auto_hunter_enabled", false)
        set(value) = prefs.edit().putBoolean("auto_hunter_enabled", value).apply()

    var stratLiquiditySweep: Boolean
        get() = prefs.getBoolean("strat_liq_sweep", true)
        set(value) = prefs.edit().putBoolean("strat_liq_sweep", value).apply()

    var stratFvgTap: Boolean
        get() = prefs.getBoolean("strat_fvg_tap", true)
        set(value) = prefs.edit().putBoolean("strat_fvg_tap", value).apply()

    var stratBreakoutMomentum: Boolean
        get() = prefs.getBoolean("strat_breakout", false)
        set(value) = prefs.edit().putBoolean("strat_breakout", value).apply()
}
