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
import java.text.SimpleDateFormat
import java.util.*

class ToolsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#0B0E11"))
            setPadding(32, 24, 32, 32)
        }
        val root = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }

        // Session Killzone Card (PKT Time)
        val pktTime = SimpleDateFormat("hh:mm a z", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Asia/Karachi")
        }.format(Date())

        val sessionCard = TextView(requireContext()).apply {
            text = "KILLZONE CLOCK (PKT): $pktTime\n• London Open: 12:00 PM – 03:00 PM PKT\n• New York Killzone: 05:00 PM – 09:00 PM PKT"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 13f
            setPadding(28, 24, 28, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141920"))
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#D4AF37"))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 24
            }
        }
        root.addView(sessionCard)

        // Exness XAUUSD Lot Calculator
        val title = TextView(requireContext()).apply {
            text = "EXNESS XAUUSD LOT & RISK CALCULATOR"
            setTextColor(Color.parseColor("#D4AF37"))
            textSize = 13f
            letterSpacing = 0.08f
            setPadding(0, 0, 0, 14)
        }
        root.addView(title)

        val etBalance = styledInput("Account Balance ($ USD)", "1000")
        val etRiskPct = styledInput("Risk Per Trade (%)", "1.0")
        val etSlDollars = styledInput("Stop Loss Distance ($ Gold move, e.g. 2.50)", "2.50")
        root.addView(etBalance)
        root.addView(etRiskPct)
        root.addView(etSlDollars)

        val btnCalc = Button(requireContext()).apply {
            text = "Calculate Exact Exness Lot Size"
            setTextColor(Color.parseColor("#0B0E11"))
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D4AF37"))
                cornerRadius = 16f
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 18
            }
        }
        root.addView(btnCalc)

        val tvResult = TextView(requireContext()).apply {
            text = "1 Standard Lot (1.00) on Exness XAUUSD = $100 per $1.00 Gold move."
            setTextColor(Color.parseColor("#10B981"))
            setBackgroundColor(Color.parseColor("#141920"))
            setPadding(28, 24, 28, 24)
            textSize = 14f
        }
        root.addView(tvResult)

        btnCalc.setOnClickListener {
            val bal = etBalance.text.toString().toDoubleOrNull() ?: 0.0
            val riskPct = etRiskPct.text.toString().toDoubleOrNull() ?: 0.0
            val slMove = etSlDollars.text.toString().toDoubleOrNull() ?: 0.0

            if (bal <= 0 || riskPct <= 0 || slMove <= 0) {
                tvResult.text = "Enter valid numbers above."
                return@setOnClickListener
            }

            val riskUsd = bal * (riskPct / 100.0)
            val lotSize = riskUsd / (slMove * 100.0)
            val pips = slMove * 10.0

            tvResult.text = String.format(
                Locale.US,
                "Recommended Lot: %.2f Lots\nRisk Amount: $%.2f (%.1f pips SL)",
                lotSize, riskUsd, pips
            )
        }

        scroll.addView(root)
        return scroll
    }

    private fun styledInput(hint: String, defaultVal: String) = EditText(requireContext()).apply {
        this.hint = hint
        setText(defaultVal)
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
}
