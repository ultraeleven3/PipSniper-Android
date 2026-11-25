package com.apexquant.pipsniper.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.apexquant.pipsniper.core.PeriodSummary
import com.apexquant.pipsniper.core.TradeHistoryManager
import com.apexquant.pipsniper.core.TradeRecord
import java.util.Calendar

class HistoryFragment : Fragment() {

    private lateinit var summaryContainer: LinearLayout
    private lateinit var filterRow: LinearLayout
    private lateinit var tradesContainer: LinearLayout
    private var selectedFilter = "TODAY"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(Color.parseColor("#0B0E11"))
            setPadding(32, 24, 32, 32)
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
        }
        val title = TextView(ctx).apply {
            text = "UNLIMITED TRADE LEDGER & ANALYTICS"
            setTextColor(Color.parseColor("#D4AF37"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnRefresh = TextView(ctx).apply {
            text = "Refresh"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(16, 4, 8, 4)
            setOnClickListener { renderAll() }
        }
        headerRow.addView(title)
        headerRow.addView(btnRefresh)
        root.addView(headerRow)

        summaryContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 20
            }
        }
        root.addView(summaryContainer)

        filterRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 18
            }
        }
        val filters = listOf("TODAY" to "Today", "WEEK" to "7 Days", "MONTH" to "30 Days", "ALL" to "All Years")
        filters.forEach { (code, label) ->
            val pill = TextView(ctx).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(12, 18, 12, 18)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 8
                }
                setOnClickListener {
                    selectedFilter = code
                    renderAll()
                }
            }
            pill.tag = code
            filterRow.addView(pill)
        }
        root.addView(filterRow)

        tradesContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(tradesContainer)

        scroll.addView(root)
        renderAll()
        return scroll
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) renderAll()
    }

    override fun onResume() {
        super.onResume()
        renderAll()
    }

    private fun renderAll() {
        if (!isAdded || !::summaryContainer.isInitialized) return
        val ctx = context ?: return

        val records = TradeHistoryManager.loadAll(ctx)
        val (todaySum, weekSum, monthSum) = TradeHistoryManager.computeSummaries(records)

        summaryContainer.removeAllViews()
        summaryContainer.addView(buildSummaryCard("TODAY'S TRADES SUMMARY", todaySum, "#D4AF37"))
        summaryContainer.addView(buildSummaryCard("THIS WEEK (LAST 7 DAYS)", weekSum, "#38BDF8"))
        summaryContainer.addView(buildSummaryCard("THIS MONTH (LAST 30 DAYS)", monthSum, "#A78BFA"))

        for (i in 0 until filterRow.childCount) {
            val pill = filterRow.getChildAt(i) as? TextView ?: continue
            val isSel = pill.tag == selectedFilter
            pill.setTextColor(if (isSel) Color.parseColor("#0B0E11") else Color.parseColor("#94A3B8"))
            pill.setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
            pill.background = GradientDrawable().apply {
                setColor(if (isSel) Color.parseColor("#D4AF37") else Color.parseColor("#141920"))
                cornerRadius = 14f
            }
        }

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val filtered = when (selectedFilter) {
            "TODAY" -> records.filter { it.timestamp >= cal.timeInMillis }
            "WEEK" -> records.filter { it.timestamp >= now - 7L * 24 * 60 * 60 * 1000 }
            "MONTH" -> records.filter { it.timestamp >= now - 30L * 24 * 60 * 60 * 1000 }
            else -> records
        }

        tradesContainer.removeAllViews()
        val countHeader = TextView(ctx).apply {
            text = "SHOWING ${filtered.size} RECORD(S) • TOTAL STORED: ${records.size}"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
            setPadding(0, 4, 0, 14)
        }
        tradesContainer.addView(countHeader)

        if (filtered.isEmpty()) {
            val emptyCard = TextView(ctx).apply {
                text = "No trades recorded in this period yet.\nArm a setup in Scan or enable Hunter to log trades automatically."
                setTextColor(Color.parseColor("#94A3B8"))
                setBackgroundColor(Color.parseColor("#141920"))
                setPadding(28, 28, 28, 28)
                textSize = 13f
            }
            tradesContainer.addView(emptyCard)
            return
        }

        for (trade in filtered) {
            tradesContainer.addView(buildTradeCard(trade))
        }
    }

    private fun buildSummaryCard(title: String, sum: PeriodSummary, accentHex: String): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141920"))
                cornerRadius = 18f
                setStroke(2, Color.parseColor("#242E3B"))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12
            }
        }

        val header = TextView(ctx).apply {
            text = "$title  •  Total: ${sum.total}"
            setTextColor(Color.parseColor(accentHex))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        }
        card.addView(header)

        val stats = TextView(ctx).apply {
            text = "TP Hit: ${sum.tpHit}   |   SL Hit: ${sum.slHit}   |   Invalid: ${sum.invalid}   |   Live: ${sum.active}"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 12f
        }
        card.addView(stats)
        return card
    }

    private fun buildTradeCard(t: TradeRecord): View {
        val ctx = requireContext()
        val (badgeText, badgeColor, borderColor) = when (t.status) {
            "TP_HIT" -> Triple("TP HIT", "#10B981", "#10B981")
            "SL_HIT" -> Triple("SL HIT", "#EF4444", "#EF4444")
            "INVALID" -> Triple("INVALID (NO CONFIRMATION)", "#F59E0B", "#F59E0B")
            "TRIGGERED" -> Triple("LIVE IN TRADE", "#38BDF8", "#38BDF8")
            else -> Triple("SCANNING CONFIRMATION", "#D4AF37", "#283240")
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 22, 26, 22)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141920"))
                cornerRadius = 18f
                setStroke(2, Color.parseColor(borderColor))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 14
            }
        }

        val topRow = TextView(ctx).apply {
            val dirColorTag = if (t.direction == "BUY") "LONG (BUY)" else "SHORT (SELL)"
            text = "$dirColorTag  •  $badgeText"
            setTextColor(Color.parseColor(badgeColor))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        }
        card.addView(topRow)

        val levelsRow = TextView(ctx).apply {
            text = "Entry: ${t.entry}   |   SL: ${t.sl}   |   TP: ${t.tp}"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 10, 0, 8)
        }
        card.addView(levelsRow)

        val metaRow = TextView(ctx).apply {
            text = "${t.source} • ${TradeHistoryManager.formatTimestamp(t.timestamp)}\nNote: ${t.closeReason}"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
        }
        card.addView(metaRow)

        return card
    }
}
