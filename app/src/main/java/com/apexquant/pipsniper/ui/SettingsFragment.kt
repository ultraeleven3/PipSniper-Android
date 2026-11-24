package com.apexquant.pipsniper.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.apexquant.pipsniper.core.AppUpdater
import com.apexquant.pipsniper.core.MarketDataEngine
import com.apexquant.pipsniper.core.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val prefs = PrefsManager(requireContext())
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#0B0E11"))
            setPadding(32, 24, 32, 32)
        }
        val root = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }

        // 1. Exness MT5 XAUUSD Calibration
        root.addView(sectionTitle("EXNESS MT5 XAUUSD PRICE CALIBRATION"))
        val tvOffsetInfo = TextView(requireContext()).apply {
            text = "Current Calibrated Offset: ${String.format(Locale.US, "%+.2f", prefs.exnessOffset)}\nEnter your current Exness MT5 XAUUSD Bid price below to sync live candles 1:1 with your broker."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, 0, 0, 14)
        }
        root.addView(tvOffsetInfo)

        val etExnessPrice = EditText(requireContext()).apply {
            hint = "Current Exness MT5 XAUUSD Price (e.g. 3702.50)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141920"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#283240"))
            }
            setPadding(28, 24, 28, 24)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 14
            }
        }
        root.addView(etExnessPrice)

        val btnSyncExness = styledButton("Sync Exness Spot Offset Now", "#D4AF37", "#0B0E11")
        val btnResetOffset = styledButton("Reset Offset to 0.00", "#1C232D", "#94A3B8")
        root.addView(btnSyncExness)
        root.addView(btnResetOffset)

        // 2. Gemini API Key
        root.addView(sectionTitle("GEMINI AI VISION CREDENTIALS"))
        val etKey = EditText(requireContext()).apply {
            hint = "Paste Gemini API Key (AIza...)"
            setText(prefs.apiKey)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141920"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#283240"))
            }
            setPadding(28, 24, 28, 24)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 14
            }
        }
        root.addView(etKey)

        val btnSaveKey = styledButton("Save Gemini API Key", "#10B981", "#0B0E11")
        root.addView(btnSaveKey)

        // 3. In-App Over-the-Air Updater
        root.addView(sectionTitle("PIP SNIPER APP UPDATES"))
        val btnUpdate = styledButton("Check & Install Latest APK Update", "#1C232D", "#D4AF37")
        root.addView(btnUpdate)

        val tvStatus = TextView(requireContext()).apply {
            text = "Build: v1.0.0 Institutional Terminal"
            setTextColor(Color.parseColor("#94A3B8"))
            setBackgroundColor(Color.parseColor("#141920"))
            setPadding(28, 24, 28, 24)
            textSize = 13f
        }
        root.addView(tvStatus)

        btnSyncExness.setOnClickListener {
            val mt5Price = etExnessPrice.text.toString().toDoubleOrNull()
            if (mt5Price == null || mt5Price <= 0) {
                tvStatus.text = "Enter your current Exness MT5 XAUUSD price first."
                return@setOnClickListener
            }
            tvStatus.text = "Fetching raw feed to compute exact Exness offset..."
            lifecycleScope.launch(Dispatchers.IO) {
                val rawCandles = MarketDataEngine.fetchCandles("GC=F", "1m", "1d", 0.0, 5)
                withContext(Dispatchers.Main) {
                    if (rawCandles.isNotEmpty()) {
                        val rawLast = rawCandles.last().close
                        val offset = mt5Price - rawLast
                        prefs.exnessOffset = offset
                        tvOffsetInfo.text = "Current Calibrated Offset: ${String.format(Locale.US, "%+.2f", offset)}"
                        tvStatus.text = "Synced! Raw: ${String.format(Locale.US, "%.2f", rawLast)} -> Exness: $mt5Price (Offset ${String.format(Locale.US, "%+.2f", offset)})"
                    } else {
                        tvStatus.text = "Could not reach live feed right now."
                    }
                }
            }
        }

        btnResetOffset.setOnClickListener {
            prefs.exnessOffset = 0.0
            tvOffsetInfo.text = "Current Calibrated Offset: +0.00"
            tvStatus.text = "Exness offset reset to 0.00."
        }

        btnSaveKey.setOnClickListener {
            prefs.apiKey = etKey.text.toString()
            tvStatus.text = "Gemini API Key saved securely."
        }

        btnUpdate.setOnClickListener {
            lifecycleScope.launch {
                AppUpdater.downloadAndInstallLatest(requireContext()) { status ->
                    tvStatus.text = status
                }
            }
        }

        scroll.addView(root)
        return scroll
    }

    private fun sectionTitle(text: String) = TextView(requireContext()).apply {
        this.text = text
        setTextColor(Color.parseColor("#D4AF37"))
        textSize = 12f
        letterSpacing = 0.08f
        setPadding(0, 18, 0, 12)
    }

    private fun styledButton(label: String, bgHex: String, textHex: String) = Button(requireContext()).apply {
        text = label
        setTextColor(Color.parseColor(textHex))
        isAllCaps = false
        background = GradientDrawable().apply {
            setColor(Color.parseColor(bgHex))
            cornerRadius = 16f
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 16
        }
    }
}
