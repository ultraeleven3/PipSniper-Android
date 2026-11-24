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
import com.apexquant.pipsniper.core.PrefsManager
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val prefs = PrefsManager(requireContext())
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#0B0E11"))
            setPadding(32, 24, 32, 32)
        }
        val root = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }

        // 1. Live Spot Gold Feed Info
        root.addView(sectionTitle("LIVE DATA FEED"))
        val tvFeedInfo = TextView(requireContext()).apply {
            text = "Connected directly to live Spot Gold (XAUUSDm) pricing. No manual broker offset required."
            setTextColor(Color.parseColor("#10B981"))
            setBackgroundColor(Color.parseColor("#141920"))
            setPadding(28, 24, 28, 24)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 20
            }
        }
        root.addView(tvFeedInfo)

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
            text = "Build: v1.1.0 Institutional Terminal (Live XAUUSDm)"
            setTextColor(Color.parseColor("#94A3B8"))
            setBackgroundColor(Color.parseColor("#141920"))
            setPadding(28, 24, 28, 24)
            textSize = 13f
        }
        root.addView(tvStatus)

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
        setPadding(0, 12, 0, 12)
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
