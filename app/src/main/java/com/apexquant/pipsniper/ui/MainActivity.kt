package com.apexquant.pipsniper

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.apexquant.pipsniper.core.PrefsManager
import com.apexquant.pipsniper.ui.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var tvPriceTicker: TextView

    private val scanFragment = ScanFragment()
    private val autoHunterFragment = AutoHunterFragment()
    private val historyFragment = HistoryFragment()
    private val toolsFragment = ToolsFragment()
    private val settingsFragment = SettingsFragment()
    private var activeFragment: Fragment = scanFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            prefs = PrefsManager(this)
            window.statusBarColor = Color.parseColor("#0B0E11")
            window.navigationBarColor = Color.parseColor("#11161D")

            requestRuntimePermissions()

            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#0B0E11"))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            // Top Institutional Header Bar
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#11161D"))
                setPadding(40, 32, 40, 28)
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvTitle = TextView(this).apply {
                text = "PIP SNIPER"
                setTextColor(Color.parseColor("#D4AF37"))
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.12f
            }
            val tvSub = TextView(this).apply {
                text = "INSTITUTIONAL XAUUSD TERMINAL"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 10f
                letterSpacing = 0.08f
            }
            titleCol.addView(tvTitle)
            titleCol.addView(tvSub)

            tvPriceTicker = TextView(this).apply {
                text = "XAUUSDm • ${prefs.lastPrice}"
                setTextColor(Color.parseColor("#10B981"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            }

            header.addView(titleCol)
            header.addView(tvPriceTicker)
            root.addView(header)

            // Fragment Container
            val containerId = View.generateViewId()
            val fragmentContainer = FrameLayout(this).apply {
                id = containerId
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            root.addView(fragmentContainer)

            // Bottom Navigation (5 Tabs)
            val bottomNav = BottomNavigationView(this).apply {
                setBackgroundColor(Color.parseColor("#11161D"))
                labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
                itemIconTintList = ColorStateList.valueOf(Color.parseColor("#D4AF37"))
                itemTextColor = ColorStateList.valueOf(Color.parseColor("#F8FAFC"))
                menu.add(0, 1, 0, "Scan").setIcon(android.R.drawable.ic_menu_compass)
                menu.add(0, 2, 1, "Hunter").setIcon(android.R.drawable.ic_menu_manage)
                menu.add(0, 3, 2, "History").setIcon(android.R.drawable.ic_menu_today)
                menu.add(0, 4, 3, "Tools").setIcon(android.R.drawable.ic_menu_agenda)
                menu.add(0, 5, 4, "Terminal").setIcon(android.R.drawable.ic_menu_preferences)
            }
            root.addView(bottomNav)
            setContentView(root)

            if (savedInstanceState == null) {
                supportFragmentManager.beginTransaction()
                    .add(containerId, settingsFragment, "5").hide(settingsFragment)
                    .add(containerId, toolsFragment, "4").hide(toolsFragment)
                    .add(containerId, historyFragment, "3").hide(historyFragment)
                    .add(containerId, autoHunterFragment, "2").hide(autoHunterFragment)
                    .add(containerId, scanFragment, "1")
                    .commit()
            }

            bottomNav.setOnItemSelectedListener { item ->
                val target = when (item.itemId) {
                    1 -> scanFragment
                    2 -> autoHunterFragment
                    3 -> historyFragment
                    4 -> toolsFragment
                    5 -> settingsFragment
                    else -> scanFragment
                }
                if (target != activeFragment) {
                    supportFragmentManager.beginTransaction()
                        .hide(activeFragment)
                        .show(target)
                        .commitAllowingStateLoss()
                    activeFragment = target
                }
                true
            }

            lifecycleScope.launch {
                while (isActive) {
                    tvPriceTicker.text = "${prefs.symbol} • ${prefs.lastPrice}"
                    delay(2000)
                }
            }
        } catch (t: Throwable) {
            val errScroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK) }
            val errText = TextView(this).apply {
                text = "STARTUP ERROR:\n\n${t.stackTraceToString()}"
                setTextColor(Color.parseColor("#EF4444"))
                setPadding(32, 48, 32, 32)
                textSize = 12f
            }
            errScroll.addView(errText)
            setContentView(errScroll)
        }
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}
