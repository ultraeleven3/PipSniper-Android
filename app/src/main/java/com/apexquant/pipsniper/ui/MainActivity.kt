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

    private val scanFragment = ScanFragment()
    private val hunterFragment = AutoHunterFragment()
    private val toolsFragment = ToolsFragment()
    private val settingsFragment = SettingsFragment()
    private var activeFragment: Fragment = scanFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)
        tvLiveTicker = findViewById(R.id.tvLiveTicker)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        requestNotificationPermission()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, settingsFragment, "4").hide(settingsFragment)
                .add(R.id.fragmentContainer, toolsFragment, "3").hide(toolsFragment)
                .add(R.id.fragmentContainer, hunterFragment, "2").hide(hunterFragment)
                .add(R.id.fragmentContainer, scanFragment, "1")
                .commit()
        }

        bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_scan -> scanFragment
                R.id.nav_hunter -> hunterFragment
                R.id.nav_tools -> toolsFragment
                R.id.nav_settings -> settingsFragment
                else -> scanFragment
            }
            if (target != activeFragment) {
                supportFragmentManager.beginTransaction()
                    .hide(activeFragment)
                    .show(target)
                    .commit()
                activeFragment = target
            }
            true
        }

        startHeaderTickerLoop()
    }

    private fun startHeaderTickerLoop() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val candles = MarketDataEngine.fetchCandles(
                        symbol = prefs.symbol,
                        interval = "1m",
                        range = "1d",
                        limit = 5
                    )
                    if (candles.isNotEmpty()) {
                        val price = candles.last().close
                        val formatted = String.format(Locale.US, "XAUUSDm %.2f", price)
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
