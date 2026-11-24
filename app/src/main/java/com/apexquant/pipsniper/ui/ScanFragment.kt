package com.apexquant.pipsniper.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.apexquant.pipsniper.core.GeminiVisionClient
import com.apexquant.pipsniper.core.MarketDataEngine
import com.apexquant.pipsniper.core.PrefsManager
import com.apexquant.pipsniper.service.SniperWatcherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ScanFragment : Fragment() {

    private lateinit var prefs: PrefsManager
    private lateinit var chartView: CandlestickChartView
    private lateinit var ivPreview: ImageView
    private lateinit var rbBuy: RadioButton
    private lateinit var rbSell: RadioButton
    private lateinit var etEntry: EditText
    private lateinit var etSl: EditText
    private lateinit var etTp: EditText
    private lateinit var tvStatus: TextView
    private var selectedBitmap: Bitmap? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                requireContext().contentResolver.openInputStream(it)?.use { stream ->
                    selectedBitmap = BitmapFactory.decodeStream(stream)
                    ivPreview.setImageBitmap(selectedBitmap)
                    ivPreview.visibility = View.VISIBLE
                    tvStatus.text = "Chart screenshot loaded. Tap '2. Scan Chart with Gemini AI'."
                }
            } catch (e: Exception) {
                tvStatus.text = "Could not open image: ${e.message}"
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        prefs = PrefsManager(requireContext())
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#0B0E11"))
            setPadding(32, 24, 32, 32)
        }

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(sectionTitle("LIVE SPOT GOLD (XAUUSDm) • 1M CHART"))
        chartView = CandlestickChartView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 460).apply {
                bottomMargin = 20
            }
        }
        root.addView(chartView)

        val btnRefreshChart = styledButton("Refresh Live Chart", "#1C232D", "#D4AF37")
        root.addView(btnRefreshChart)

        root.addView(sectionTitle("MODE 1 • AI CHART SCANNER"))
        val btnPick = styledButton("1. Upload TradingView / MT5 Screenshot", "#1C232D", "#F8FAFC")
        root.addView(btnPick)

        ivPreview = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 300).apply {
                bottomMargin = 16
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        root.addView(ivPreview)

        val btnScan = styledButton("2. Scan Chart with Gemini AI", "#D4AF37", "#0B0E11")
        root.addView(btnScan)

        val rg = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 8, 0, 16)
        }
        rbBuy = RadioButton(requireContext()).apply {
            text = "BUY (LONG)"
            setTextColor(Color.parseColor("#10B981"))
            isChecked = true
            setPadding(0, 0, 48, 0)
        }
        rbSell = RadioButton(requireContext()).apply {
            text = "SELL (SHORT)"
            setTextColor(Color.parseColor("#EF4444"))
        }
        rg.addView(rbBuy)
        rg.addView(rbSell)
        root.addView(rg)

        etEntry = styledInput("Entry Price (XAUUSDm)")
        etSl = styledInput("Stop Loss (SL)")
        etTp = styledInput("Take Profit (TP)")
        root.addView(etEntry)
        root.addView(etSl)
        root.addView(etTp)

        val btnArm = styledButton("3. Arm 1m Confirmation Sniper", "#10B981", "#0B0E11")
        val btnStop = styledButton("Disarm & Clear Active Setups", "#1C232D", "#EF4444")
        root.addView(btnArm)
        root.addView(btnStop)

        tvStatus = TextView(requireContext()).apply {
            text = "Terminal Ready. Live XAUUSDm Spot Gold active."
            setTextColor(Color.parseColor("#94A3B8"))
            setBackgroundColor(Color.parseColor("#141920"))
            setPadding(28, 24, 28, 24)
            textSize = 13f
        }
        root.addView(tvStatus)

        scroll.addView(root)

        btnRefreshChart.setOnClickListener {
            tvStatus.text = "Refreshing live XAUUSDm candles..."
            loadChart(manual = true)
        }
        btnPick.setOnClickListener { pickImage.launch("image/*") }

        btnScan.setOnClickListener {
            val key = prefs.apiKey
            if (key.isEmpty()) {
                tvStatus.text = "Missing API Key! Paste your Gemini API key in the Terminal tab first."
                return@setOnClickListener
            }
            val bmp = selectedBitmap
            if (bmp == null) {
                tvStatus.text = "Pick a chart screenshot first."
                return@setOnClickListener
            }
            btnScan.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                val res = GeminiVisionClient.scanChart(key, bmp) { msg ->
                    lifecycleScope.launch(Dispatchers.Main) { tvStatus.text = msg }
                }
                withContext(Dispatchers.Main) {
                    btnScan.isEnabled = true
                    if (res.success) {
                        if (res.direction == "SELL") rbSell.isChecked = true else rbBuy.isChecked = true
                        if (res.entry > 0) etEntry.setText(res.entry.toString())
                        if (res.sl > 0) etSl.setText(res.sl.toString())
                        if (res.tp > 0) etTp.setText(res.tp.toString())
                        tvStatus.text = "AI Extracted: ${res.direction} | Entry: ${res.entry} | SL: ${res.sl} | TP: ${res.tp}"
                        loadChart(manual = false)
                    } else {
                        tvStatus.text = "Scan Failed: ${res.errorMessage}"
                    }
                }
            }
        }

        btnArm.setOnClickListener {
            val entry = etEntry.text.toString().toDoubleOrNull()
            val sl = etSl.text.toString().toDoubleOrNull()
            val tp = etTp.text.toString().toDoubleOrNull()
            if (entry == null || sl == null || tp == null) {
                tvStatus.text = "Enter valid Entry, SL, and TP levels first."
                return@setOnClickListener
            }
            val dir = if (rbSell.isChecked) "SELL" else "BUY"
            val obj = JSONObject().apply {
                put("direction", dir)
                put("entry", entry)
                put("sl", sl)
                put("tp", tp)
                put("tapped", false)
                put("tap_ts", 0L)
            }
            prefs.setupsJson = JSONArray().apply { put(obj) }.toString()
            ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), SniperWatcherService::class.java))
            tvStatus.text = "SNIPER ARMED: $dir @ $entry | SL: $sl | TP: $tp"
            loadChart(manual = false)
        }

        btnStop.setOnClickListener {
            prefs.setupsJson = "[]"
            if (!prefs.autoHunterEnabled) {
                requireContext().stopService(Intent(requireContext(), SniperWatcherService::class.java))
            }
            tvStatus.text = "Manual setups cleared."
            loadChart(manual = false)
        }

        startLiveChartLoop()
        return scroll
    }

    private fun startLiveChartLoop() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                loadChartSuspend(manual = false)
                delay(15_000)
            }
        }
    }

    private fun loadChart(manual: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            loadChartSuspend(manual)
        }
    }

    private suspend fun loadChartSuspend(manual: Boolean) {
        try {
            val candles = MarketDataEngine.fetchCandles(
                symbol = prefs.symbol,
                interval = "1m",
                range = "1d",
                limit = 35
            )
            withContext(Dispatchers.Main) {
                if (candles.isNotEmpty()) {
                    val lastClose = candles.last().close
                    prefs.lastPrice = String.format(Locale.US, "%.2f", lastClose)
                    chartView.updateData(
                        candles,
                        etEntry.text.toString().toDoubleOrNull(),
                        etSl.text.toString().toDoubleOrNull(),
                        etTp.text.toString().toDoubleOrNull()
                    )
                    if (manual) {
                        tvStatus.text = "Live chart updated • XAUUSDm: ${String.format(Locale.US, "%.2f", lastClose)}"
                    }
                } else if (manual) {
                    tvStatus.text = "Market feed temporarily unreachable. Check internet connection."
                }
            }
        } catch (e: Exception) {
            if (manual) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Feed error: ${e.message}"
                }
            }
        }
    }

    private fun sectionTitle(text: String) = TextView(requireContext()).apply {
        this.text = text
        setTextColor(Color.parseColor("#D4AF37"))
        textSize = 12f
        letterSpacing = 0.08f
        setPadding(0, 12, 0, 12)
    }

    private fun styledInput(hint: String) = EditText(requireContext()).apply {
        this.hint = hint
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
            bottomMargin = 16
        }
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
