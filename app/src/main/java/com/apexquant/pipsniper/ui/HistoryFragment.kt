package com.apexquant.pipsniper.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.apexquant.pipsniper.R
import com.apexquant.pipsniper.core.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private var activeLedger: TradeLedger = TradeLedger.MANUAL
    private val displayedMonth: Calendar = Calendar.getInstance()
    private var selectedDayKey: String? = null

    private lateinit var tabManual: TextView
    private lateinit var tabScan: TextView
    private lateinit var tabBot: TextView
    private lateinit var tvWinRate: TextView
    private lateinit var tvNetPnl: TextView
    private lateinit var tvNetR: TextView
    private lateinit var tvRecord: TextView
    private lateinit var tvMonthTitle: TextView
    private lateinit var calendarView: PnlCalendarView
    private lateinit var tvListHeader: TextView
    private lateinit var tvClearDay: TextView
    private lateinit var cardsContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        tabManual = view.findViewById(R.id.tabLedgerManual)
        tabScan = view.findViewById(R.id.tabLedgerScan)
        tabBot = view.findViewById(R.id.tabLedgerBot)
        tvWinRate = view.findViewById(R.id.tvKpiWinRate)
        tvNetPnl = view.findViewById(R.id.tvKpiNetPnl)
        tvNetR = view.findViewById(R.id.tvKpiNetR)
        tvRecord = view.findViewById(R.id.tvKpiRecord)
        tvMonthTitle = view.findViewById(R.id.tvCalendarMonthTitle)
        calendarView = view.findViewById(R.id.pnlCalendarView)
        tvListHeader = view.findViewById(R.id.tvTradeListHeader)
        tvClearDay = view.findViewById(R.id.tvClearDayFilter)
        cardsContainer = view.findViewById(R.id.tradeCardsContainer)

        tabManual.setOnClickListener { switchLedger(TradeLedger.MANUAL) }
        tabScan.setOnClickListener { switchLedger(TradeLedger.AI_SCAN) }
        tabBot.setOnClickListener { switchLedger(TradeLedger.BOT_AUTO) }

        view.findViewById<Button>(R.id.btnPrevMonth).setOnClickListener {
            displayedMonth.add(Calendar.MONTH, -1)
            selectedDayKey = null
            refreshDashboard()
        }

        view.findViewById<Button>(R.id.btnNextMonth).setOnClickListener {
            displayedMonth.add(Calendar.MONTH, 1)
            selectedDayKey = null
            refreshDashboard()
        }

        tvClearDay.setOnClickListener {
            selectedDayKey = null
            refreshDashboard()
        }

        calendarView.onDaySelected = { dayKey ->
            selectedDayKey = dayKey
            refreshDashboard()
        }

        view.findViewById<Button>(R.id.btnLogManualTrade).setOnClickListener {
            showManualTradeDialog()
        }

        view.findViewById<Button>(R.id.btnExportCsv).setOnClickListener {
            val csv = TradeHistoryManager.exportToCsv(requireContext(), activeLedger)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "PipSniper_${activeLedger.key}_Ledger.csv")
                putExtra(Intent.EXTRA_TEXT, csv)
            }
            startActivity(Intent.createChooser(sendIntent, "Export ${activeLedger.label} CSV"))
        }

        switchLedger(TradeLedger.MANUAL)
        return view
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun switchLedger(ledger: TradeLedger) {
        activeLedger = ledger
        selectedDayKey = null

        val tabs = listOf(
            tabManual to TradeLedger.MANUAL,
            tabScan to TradeLedger.AI_SCAN,
            tabBot to TradeLedger.BOT_AUTO
        )
        for ((tv, item) in tabs) {
            if (item == ledger) {
                tv.setBackgroundColor(Color.parseColor("#F5B041"))
                tv.setTextColor(Color.parseColor("#0B0E14"))
            } else {
                tv.setBackgroundColor(Color.TRANSPARENT)
                tv.setTextColor(Color.parseColor("#9CA3AF"))
            }
        }
        refreshDashboard()
    }

    private fun refreshDashboard() {
        val ctx = context ?: return
        val year = displayedMonth.get(Calendar.YEAR)
        val month = displayedMonth.get(Calendar.MONTH)
        val monthPrefix = String.format(Locale.US, "%04d-%02d", year, month + 1)

        val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.US)
        tvMonthTitle.text = monthFormatter.format(displayedMonth.time)

        val ledgerTrades = TradeHistoryManager.getAllTrades(ctx)
            .filter { it.ledger == activeLedger }

        val monthTrades = ledgerTrades.filter { it.dayKey().startsWith(monthPrefix) }

        val dayMap = mutableMapOf<String, DayPnlSummary>()
        for (trade in monthTrades) {
            val key = trade.dayKey()
            val prev = dayMap[key]
            val pips = trade.realizedPips()
            val usd = trade.pnlUsd ?: 0.0
            val hasUsd = (trade.pnlUsd != null) || (prev?.hasUsd == true)
            val winInc = if (trade.status == "TP_HIT") 1 else 0
            val lossInc = if (trade.status == "SL_HIT") 1 else 0

            dayMap[key] = DayPnlSummary(
                dayKey = key,
                netPips = (prev?.netPips ?: 0.0) + pips,
                netUsd = (prev?.netUsd ?: 0.0) + usd,
                hasUsd = hasUsd,
                tradeCount = (prev?.tradeCount ?: 0) + 1,
                wins = (prev?.wins ?: 0) + winInc,
                losses = (prev?.losses ?: 0) + lossInc
            )
        }

        calendarView.setMonthData(year, month, dayMap, selectedDayKey)

        val scopeTrades = if (selectedDayKey != null) {
            monthTrades.filter { it.dayKey() == selectedDayKey }
        } else {
            monthTrades
        }

        val wins = scopeTrades.count { it.status == "TP_HIT" }
        val losses = scopeTrades.count { it.status == "SL_HIT" }
        val decided = wins + losses
        val winRate = if (decided > 0) (wins.toDouble() / decided) * 100.0 else 0.0
        val totalPips = scopeTrades.sumOf { it.realizedPips() }
        val totalR = scopeTrades.sumOf { it.realizedR() }
        val totalUsd = scopeTrades.mapNotNull { it.pnlUsd }.sum()
        val anyUsd = scopeTrades.any { it.pnlUsd != null }

        tvWinRate.text = String.format(Locale.US, "%.1f%%", winRate)
        tvRecord.text = "${wins}W - ${losses}L"
        tvNetR.text = String.format(Locale.US, "%+.2fR", totalR)
        tvNetPnl.text = if (anyUsd) {
            String.format(Locale.US, "%+.0fp ($%+.2f)", totalPips, totalUsd)
        } else {
            String.format(Locale.US, "%+.1fp", totalPips)
        }
        tvNetPnl.setTextColor(
            if (totalPips >= 0) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
        )

        if (selectedDayKey != null) {
            tvListHeader.text = "TRADES ON $selectedDayKey (${scopeTrades.size})"
            tvClearDay.visibility = View.VISIBLE
        } else {
            tvListHeader.text = "${activeLedger.label.uppercase(Locale.US)} — ${monthFormatter.format(displayedMonth.time).uppercase(Locale.US)} (${scopeTrades.size})"
            tvClearDay.visibility = View.GONE
        }

        renderTradeCards(scopeTrades)
    }

    private fun renderTradeCards(trades: List<TradeRecord>) {
        cardsContainer.removeAllViews()
        val ctx = context ?: return

        if (trades.isEmpty()) {
            val emptyTv = TextView(ctx).apply {
                text = "No trades logged in ${activeLedger.label} for this selection."
                setTextColor(Color.parseColor("#6B7280"))
                textSize = 13f
                setPadding(12, 32, 12, 32)
            }
            cardsContainer.addView(emptyTv)
            return
        }

        val timeFmt = SimpleDateFormat("dd MMM • HH:mm", Locale.US)

        for (trade in trades) {
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#131722"))
                setPadding(32, 26, 32, 26)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 18
                layoutParams = params
            }

            val topRow = TextView(ctx).apply {
                val badgeColor = if (trade.direction == "BUY") "#10B981" else "#EF4444"
                setTextColor(Color.parseColor(badgeColor))
                textSize = 13.5f
                paint.isFakeBoldText = true
                text = "${trade.direction} ${trade.symbol}  •  [${trade.status}]  •  ${trade.session}"
            }

            val levelsRow = TextView(ctx).apply {
                setTextColor(Color.parseColor("#E5E7EB"))
                textSize = 12.5f
                setPadding(0, 8, 0, 4)
                val pnlStr = when {
                    trade.pnlUsd != null -> String.format(Locale.US, " | P&L: $%+.2f", trade.pnlUsd)
                    trade.status in listOf("TP_HIT", "SL_HIT") -> String.format(Locale.US, " | %+.1f pips (%.2fR)", trade.realizedPips(), trade.realizedR())
                    else -> String.format(Locale.US, " | R:R 1:%.2f", trade.plannedRR())
                }
                text = "Entry: ${trade.entry}  |  SL: ${trade.sl}  |  TP: ${trade.tp}$pnlStr"
            }

            val metaRow = TextView(ctx).apply {
                setTextColor(Color.parseColor("#8A94A6"))
                textSize = 11f
                val notePreview = if (trade.notes.isNotBlank()) " • ${trade.notes}" else ""
                text = "${timeFmt.format(Date(trade.effectiveTimestamp()))} • Lot ${trade.lotSize}$notePreview"
            }

            card.addView(topRow)
            card.addView(levelsRow)
            card.addView(metaRow)

            card.setOnClickListener { showTradeActionDialog(trade) }
            cardsContainer.addView(card)
        }
    }

    private fun showTradeActionDialog(trade: TradeRecord) {
        val ctx = context ?: return
        val options = mutableListOf(
            "Mark TP Hit (+${String.format(Locale.US, "%.0f", trade.rewardPips())}p)",
            "Mark SL Hit (-${String.format(Locale.US, "%.0f", trade.riskPips())}p)",
            "Mark Breakeven (BE)",
            "Mark Invalid / Cancelled"
        )
        if (trade.ledger != TradeLedger.MANUAL) {
            options.add("Copy to My MT5 Journal")
        }
        options.add("Delete Record")

        AlertDialog.Builder(ctx)
            .setTitle("${trade.direction} ${trade.symbol} @ ${trade.entry}")
            .setItems(options.toTypedArray()) { _, which ->
                val choice = options[which]
                when {
                    choice.startsWith("Mark TP") -> TradeHistoryManager.updateStatus(ctx, trade.id, "TP_HIT")
                    choice.startsWith("Mark SL") -> TradeHistoryManager.updateStatus(ctx, trade.id, "SL_HIT")
                    choice.startsWith("Mark Breakeven") -> TradeHistoryManager.updateStatus(ctx, trade.id, "BE")
                    choice.startsWith("Mark Invalid") -> TradeHistoryManager.updateStatus(ctx, trade.id, "INVALID")
                    choice.startsWith("Copy to My MT5") -> {
                        TradeHistoryManager.promoteToManualJournal(ctx, trade)
                        Toast.makeText(ctx, "Copied to My MT5 Journal", Toast.LENGTH_SHORT).show()
                    }
                    choice == "Delete Record" -> TradeHistoryManager.deleteTrade(ctx, trade.id)
                }
                refreshDashboard()
            }
            .show()
    }

    private fun showManualTradeDialog() {
        val ctx = context ?: return
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val dirSpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, listOf("BUY", "SELL"))
        }
        val statusSpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, listOf("TP_HIT", "SL_HIT", "BE", "TRIGGERED"))
        }
        val etEntry = EditText(ctx).apply { hint = "Entry Price (e.g. 3702.50)" }
        val etSl = EditText(ctx).apply { hint = "Stop Loss (e.g. 3699.50)" }
        val etTp = EditText(ctx).apply { hint = "Take Profit (e.g. 3714.50)" }
        val etLot = EditText(ctx).apply { hint = "Lot Size (default 0.01)" }
        val etUsd = EditText(ctx).apply { hint = "Exact Net Profit/Loss USD (optional, e.g. 12.50 or -6.00)" }
        val etNotes = EditText(ctx).apply { hint = "Setup / Notes (e.g. London Liquidity Sweep)" }

        container.addView(dirSpinner)
        container.addView(statusSpinner)
        container.addView(etEntry)
        container.addView(etSl)
        container.addView(etTp)
        container.addView(etLot)
        container.addView(etUsd)
        container.addView(etNotes)

        AlertDialog.Builder(ctx)
            .setTitle("Log Executed MT5 Trade")
            .setView(container)
            .setPositiveButton("Save to My MT5 Journal") { _, _ ->
                val entry = etEntry.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                val sl = etSl.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                val tp = etTp.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                val lot = etLot.text.toString().toDoubleOrNull() ?: 0.01
                val usd = etUsd.text.toString().toDoubleOrNull()
                val status = statusSpinner.selectedItem.toString()

                val record = TradeRecord(
                    ledger = TradeLedger.MANUAL,
                    direction = dirSpinner.selectedItem.toString(),
                    entry = entry,
                    sl = sl,
                    tp = tp,
                    lotSize = lot,
                    pnlUsd = usd,
                    status = status,
                    notes = etNotes.text.toString().trim(),
                    closedAt = if (status in listOf("TP_HIT", "SL_HIT", "BE")) System.currentTimeMillis() else null
                )
                TradeHistoryManager.addTrade(ctx, record)
                switchLedger(TradeLedger.MANUAL)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
