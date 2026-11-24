package com.apexquant.pipsniper.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.apexquant.pipsniper.core.PrefsManager
import com.apexquant.pipsniper.service.SniperWatcherService

class AutoHunterFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val prefs = PrefsManager(requireContext())
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#0B0E11"))
            setPadding(32, 24, 32, 32)
        }
        val root = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }

        val header = TextView(requireContext()).apply {
            text = "MODE 2 • AUTONOMOUS STRATEGY HUNTER"
            setTextColor(Color.parseColor("#D4AF37"))
            textSize = 13f
            letterSpacing = 0.08f
            setPadding(0, 0, 0, 8)
        }
        val desc = TextView(requireContext()).apply {
            text = "Pip Sniper continuously scans Exness XAUUSD 1m structure and fires alarm signals with Entry, SL & TP whenever an enabled strategy triggers."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, 0, 0, 24)
        }
        root.addView(header)
        root.addView(desc)

        val masterSwitch = createCardToggle(
            title = "Master Autonomous Hunter",
            subtitle = "Run 24/7 background strategy scanning",
            checked = prefs.autoHunterEnabled
        ) { enabled ->
            prefs.autoHunterEnabled = enabled
            if (enabled) {
                ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), SniperWatcherService::class.java))
            } else if (prefs.getSetups().length() == 0) {
                requireContext().stopService(Intent(requireContext(), SniperWatcherService::class.java))
            }
        }
        root.addView(masterSwitch)

        root.addView(createCardToggle(
            title = "Strategy 1: SMC Liquidity Sweep",
            subtitle = "Detects 15m swing high/low sweeps followed by strong 1m rejection close (1:2.5 RR)",
            checked = prefs.stratLiquiditySweep
        ) { prefs.stratLiquiditySweep = it })

        root.addView(createCardToggle(
            title = "Strategy 2: Fair Value Gap (FVG) Tap",
            subtitle = "Detects 3-candle institutional imbalance taps with continuation close (1:2.2 RR)",
            checked = prefs.stratFvgTap
        ) { prefs.stratFvgTap = it })

        root.addView(createCardToggle(
            title = "Strategy 3: Momentum Displacement Breakout",
            subtitle = "Detects high-body breakout candles clearing 10m consolidation ranges (1:2.0 RR)",
            checked = prefs.stratBreakoutMomentum
        ) { prefs.stratBreakoutMomentum = it })

        scroll.addView(root)
        return scroll
    }

    private fun createCardToggle(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(28, 24, 28, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141920"))
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#283240"))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 18
            }
        }
        val textCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvTitle = TextView(requireContext()).apply {
            text = title
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 15f
        }
        val tvSub = TextView(requireContext()).apply {
            text = subtitle
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, 6, 16, 0)
        }
        textCol.addView(tvTitle)
        textCol.addView(tvSub)

        val sw = Switch(requireContext()).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }
        card.addView(textCol)
        card.addView(sw)
        return card
    }
}
