package com.apexquant.pipsniper.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.apexquant.pipsniper.R
import com.apexquant.pipsniper.core.MarketDataEngine
import com.apexquant.pipsniper.core.PrefsManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var tvLiveTicker: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)
        tvLiveTicker = findViewById(R.id.tvLiveTicker)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        requestNotificationPermission()

        if (savedInstanceState == null) {
            switchFragment(ScanFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scan -> switchFragment(ScanFragment())
                R.id.nav_hunter -> switchFragment(AutoHunterFragment())
                R.id.nav_tools -> switchFragment(ToolsFragment())
                R.id.nav_settings -> switchFragment(SettingsFragment())
            }
            true
        }

        startHeaderTickerLoop()
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun startHeaderTickerLoop() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val candles = MarketDataEngine.fetchCandles(
                        symbol = prefs.symbol,
                        interval = "1m",
                        range = "1d",
                        exnessOffset = prefs.exnessOffset,
                        limit = 5
                    )
                    if (candles.isNotEmpty()) {
                        val price = candles.last().close
                        val formatted = String.format(Locale.US, "XAUUSD %.2f", price)
                        prefs.lastPrice = String.format(Locale.US, "%.2f", price)
                        withContext(Dispatchers.Main) {
                            tvLiveTicker.text = formatted
                        }
                    }
                } catch (_: Exception) {}
                delay(15_000)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }
}
