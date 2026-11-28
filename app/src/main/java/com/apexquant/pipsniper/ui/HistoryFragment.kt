package com.apexquant.pipsniper.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.apexquant.pipsniper.R
import com.apexquant.pipsniper.core.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class HistoryFragment : Fragment() {

    private var activeLedger: TradeLedger = TradeLedger.MANUAL
    private var activeScale: CalendarScale = CalendarScale.DAY
    private val displayedDate: Calendar = Calendar.getInstance()
    private var selectedDayKey: String? = null

    private lateinit var tabManual: TextView
    private lateinit var tabScan: TextView
    private lateinit var tabBot: TextView
    private lateinit var tvWinRate: TextView
    private lateinit var tvNetPnl: TextView
    private lateinit var tvNetR: TextView
    private lateinit var tvRecord: TextView

    private lateinit var tvDailyTargetStatus: TextView
    private lateinit var pbDailyTarget: ProgressBar
    private lateinit var tvMonthlyTargetStatus: TextView
    private lateinit var pbMonthlyTarget: ProgressBar
    private lateinit var tvGuardrailWarning: TextView

    private lateinit var tvMonthTitle: TextView
    private lateinit var btnPrevPeriod: Button
    private lateinit var btnNextPeriod: Button
    private lateinit var btnScaleDay: TextView
    private lateinit var btnScaleMonth: TextView
    private lateinit var btnScaleYear: TextView
    private lateinit var calendarView: PnlCalendarView
    private lateinit var chartsView: PerformanceChartsView

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

        tvDailyTargetStatus = view.findViewById(R.id.tvDailyTargetStatus)
        pbDailyTarget = view.findViewById(R.id.pbDailyTarget)
        tvMonthlyTargetStatus = view.findViewById(R.id.tvMonthlyTargetStatus)
        pbMonthlyTarget = view.findViewById(R.id.pbMonthlyTarget)
        tvGuardrailWarning = view.findViewById(R.id.tvGuardrailWarning)

        tvMonthTitle = view.findViewById(R.id.tvCalendarMonthTitle)
        btnPrevPeriod = view.findViewById(R.id.btnPrevMonth)
        btnNextPeriod = view.findViewById(R.id.btnNextMonth)
        btnScaleDay = view.findViewById(R.id.btnScaleDay)
        btnScaleMonth = view.findViewById(R.id.btnScaleMonth)
        btnScaleYear = view.findViewById(R.id.btnScaleYear)
        calendarView = view.findViewById(R.id.pnlCalendarView)
        chartsView = view.findViewById(R.id.performanceChartsView)

        tvListHeader = view.findViewById(R.id.tvTradeListHeader)
        tvClearDay = view.findViewById(R.id.tvClearDayFilter)
        cardsContainer = view.findViewById(R.id.tradeCardsContainer)

        tabManual.setOnClickListener { switchLedger(TradeLedger.MANUAL) }
        tabScan.setOnClickListener { switchLedger(TradeLedger.AI_SCAN) }
        tabBot.setOnClickListener { switchLedger(TradeLedger.BOT_AUTO) }

        btnScaleDay.setOnClickListener { switchScale(CalendarScale.DAY) }
        btnScaleMonth.setOnClickListener { switchScale(CalendarScale.MONTH) }
        btnScaleYear.setOnClickListener { switchScale(CalendarScale.YEAR) }

        btnPrevPeriod.setOnClickListener {
            when (activeScale) {
                CalendarScale.DAY -> displayedDate.add(Calendar.MONTH, -1)
                CalendarScale.MONTH -> displayedDate.add(Calendar.YEAR, -1)
                CalendarScale.YEAR -> displayedDate.add(Calendar.YEAR, -3)
            }
            selectedDayKey = null
            refreshDashboard()
        }

        btnNextPeriod.setOnClickListener {
            when (activeScale) {
                CalendarScale.DAY -> displayedDate.add(Calendar.MONTH, 1)
                CalendarScale.MONTH -> displayedDate.add(Calendar.YEAR, 1)
                CalendarScale.YEAR -> displayedDate.add(Calendar.YEAR, 3)
            }
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

        calendarView.onMonthDrilled = { targetMonth ->
            displayedDate.set(Calendar.MONTH, targetMonth)
            selectedDayKey = null
            switchScale(CalendarScale.DAY)
        }

        calendarView.onYearDrilled = { targetYear ->
            displayedDate.set(Calendar.YEAR, targetYear)
            selectedDayKey = null
            switchScale(CalendarScale.MONTH)
        }

        view.findViewById<Button>(R.id.btnLogManualTrade).setOnClickListener {
            showTradeEntryOrEditDialog(existingTrade = null)
        }

        view.findViewById<TextView>(R.id.btnEditGoals).setOnClickListener {
            showEditGoalsDialog()
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

    private fun switchScale(scale: CalendarScale) {
        activeScale = scale
        val buttons = listOf(
            btnScaleDay to CalendarScale.DAY,
            btnScaleMonth to CalendarScale.MONTH,
            btnScaleYear to CalendarScale.YEAR
        )
        for ((tv, item) in buttons) {
            if (item == scale) {
                tv.setBackgroundColor(Color.parseColor("#F5B041"))
                tv.setTextColor(Color.parseColor("#0B0E14"))
            } else {
                tv.setBackgroundColor(Color.TRANSPARENT)
                tv.setTextColor(Color.parseColor("#9CA3AF"))
            }
        }
        refreshDashboard()
    }

    private fun computeTradeUsd(t: TradeRecord): Double {
        return t.pnlUsd ?: (t.realizedPips() * t.lotSize * 10.0)
    }

    private fun refreshDashboard() {
        val ctx = context ?: return
        val year = displayedDate.get(Calendar.YEAR)
        val month = displayedDate.get(Calendar.MONTH)
        val monthPrefix = String.format(Locale.US, "%04d-%02d", year, month + 1)
        val yearPrefix = String.format(Locale.US, "%04d", year)

        val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.US)
        tvMonthTitle.text = when (activeScale) {
            CalendarScale.DAY -> monthFormatter.format(displayedDate.time)
            CalendarScale.MONTH -> "$year (12-Month Heatmap)"
            CalendarScale.YEAR -> "Multi-Year Performance"
        }

        val ledgerTrades = TradeHistoryManager.getAllTrades(ctx)
            .filter { it.ledger == activeLedger }

        // Build summaries for Day, Month, and Year
        val periodMap = mutableMapOf<String, PeriodPnlSummary>()
        val yearsSet = mutableSetOf(year, Calendar.getInstance().get(Calendar.YEAR))

        for (trade in ledgerTrades) {
            val dKey = trade.dayKey() // yyyy-MM-dd
            val mKey = dKey.take(7)   // yyyy-MM
            val yKey = dKey.take(4)   // yyyy
            yKey.toIntOrNull()?.let { yearsSet.add(it) }

            val keysToAccumulate = when (activeScale) {
                CalendarScale.DAY -> if (dKey.startsWith(monthPrefix)) listOf(dKey) else emptyList()
                CalendarScale.MONTH -> if (mKey.startsWith(yearPrefix)) listOf(mKey) else emptyList()
                CalendarScale.YEAR -> listOf(yKey)
            }

            for (k in keysToAccumulate) {
                val prev = periodMap[k]
                val pips = trade.realizedPips()
                val usd = computeTradeUsd(trade)
                val winInc = if (trade.status == "TP_HIT") 1 else 0
                val lossInc = if (trade.status == "SL_HIT") 1 else 0
                periodMap[k] = PeriodPnlSummary(
                    key = k,
                    netPips = (prev?.netPips ?: 0.0) + pips,
                    netUsd = (prev?.netUsd ?: 0.0) + usd,
                    hasUsd = true,
                    tradeCount = (prev?.tradeCount ?: 0) + 1,
                    wins = (prev?.wins ?: 0) + winInc,
                    losses = (prev?.losses ?: 0) + lossInc
                )
            }
        }

        calendarView.setCalendarData(
            targetScale = activeScale,
            targetYear = year,
            targetMonth = month,
            periodSummaries = periodMap,
            availableYears = yearsSet.sorted(),
            selectedFilterKey = selectedDayKey
        )

        // Scope trades based on active scale & selection
        val periodTrades = when (activeScale) {
            CalendarScale.DAY -> ledgerTrades.filter { it.dayKey().startsWith(monthPrefix) }
            CalendarScale.MONTH -> ledgerTrades.filter { it.dayKey().startsWith(yearPrefix) }
            CalendarScale.YEAR -> ledgerTrades
        }

        val scopeTrades = if (activeScale == CalendarScale.DAY && selectedDayKey != null) {
            periodTrades.filter { it.dayKey() == selectedDayKey }
        } else {
            periodTrades
        }

        // Update KPI strip
        val wins = scopeTrades.count { it.status == "TP_HIT" }
        val losses = scopeTrades.count { it.status == "SL_HIT" }
        val decided = wins + losses
        val winRate = if (decided > 0) (wins.toDouble() / decided) * 100.0 else 0.0
        val totalPips = scopeTrades.sumOf { it.realizedPips() }
        val totalR = scopeTrades.sumOf { it.realizedR() }
        val totalUsd = scopeTrades.sumOf { computeTradeUsd(it) }

        tvWinRate.text = String.format(Locale.US, "%.1f%%", winRate)
        tvRecord.text = "${wins}W - ${losses}L"
        tvNetR.text = String.format(Locale.US, "%+.2fR", totalR)
        tvNetPnl.text = String.format(Locale.US, "$%+.2f (%+.0fp)", totalUsd, totalPips)
        tvNetPnl.setTextColor(
            if (totalUsd >= 0) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
        )

        // Update Goals & Risk Guardrails Card (always measured against Today & Current Month)
        updateGoalsCard(ledgerTrades)

        // Update Equity Curve & Session Bar Chart
        chartsView.setTrades(periodTrades)

        // Update List Header & Trade Cards
        if (activeScale == CalendarScale.DAY && selectedDayKey != null) {
            tvListHeader.text = "TRADES ON $selectedDayKey (${scopeTrades.size})"
            tvClearDay.visibility = View.VISIBLE
        } else {
            val scopeLabel = when (activeScale) {
                CalendarScale.DAY -> monthFormatter.format(displayedDate.time).uppercase(Locale.US)
                CalendarScale.MONTH -> "YEAR $year"
                CalendarScale.YEAR -> "ALL YEARS"
            }
            tvListHeader.text = "${activeLedger.label.uppercase(Locale.US)} — $scopeLabel (${scopeTrades.size})"
            tvClearDay.visibility = View.GONE
        }

        renderTradeCards(scopeTrades)
    }

    private fun updateGoalsCard(ledgerTrades: List<TradeRecord>) {
        val ctx = context ?: return
        val goals = GoalsManager.getGoals(ctx)
        val now = Calendar.getInstance()
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        val currentMonthPrefix = todayKey.take(7)

        val todayTrades = ledgerTrades.filter { it.dayKey() == todayKey }
        val monthTrades = ledgerTrades.filter { it.dayKey().startsWith(currentMonthPrefix) }

        val todayUsd = todayTrades.sumOf { computeTradeUsd(it) }
        val monthUsd = monthTrades.sumOf { computeTradeUsd(it) }

        val dailyPct = if (goals.dailyProfitTargetUsd > 0) {
            ((todayUsd / goals.dailyProfitTargetUsd) * 100.0).toInt().coerceIn(0, 100)
        } else 0
        val monthlyPct = if (goals.monthlyProfitTargetUsd > 0) {
            ((monthUsd / goals.monthlyProfitTargetUsd) * 100.0).toInt().coerceIn(0, 100)
        } else 0

        tvDailyTargetStatus.text = String.format(
            Locale.US,
            "Today Target: $%+.2f / $%.0f (%d%%)",
            todayUsd,
            goals.dailyProfitTargetUsd,
            dailyPct
        )
        pbDailyTarget.progress = dailyPct
        pbDailyTarget.progressTintList = ColorStateList.valueOf(
            if (todayUsd >= goals.dailyProfitTargetUsd) Color.parseColor("#10B981") else Color.parseColor("#3B82F6")
        )

        tvMonthlyTargetStatus.text = String.format(
            Locale.US,
            "Monthly Target: $%+.2f / $%.0f (%d%%)",
            monthUsd,
            goals.monthlyProfitTargetUsd,
            monthlyPct
        )
        pbMonthlyTarget.progress = monthlyPct

        val lossBreached = todayUsd <= -abs(goals.dailyMaxLossUsd)
        val overtraded = todayTrades.size > goals.dailyMaxTrades
        tvGuardrailWarning.text = when {
            lossBreached -> String.format(
                Locale.US,
                "MAX LOSS HIT ($%+.2f / -$%.0f) — STOP TRADING TODAY!",
                todayUsd,
                abs(goals.dailyMaxLossUsd)
            )
            overtraded -> "OVERTRADING ALERT: ${todayTrades.size} / ${goals.dailyMaxTrades} max trades today"
            else -> String.format(
                Locale.US,
                "Daily Guardrail: $%+.2f / -$%.0f max loss • %d / %d trades today",
                todayUsd,
                abs(goals.dailyMaxLossUsd),
                todayTrades.size,
                goals.dailyMaxTrades
            )
        }
        tvGuardrailWarning.setTextColor(
            when {
                lossBreached -> Color.parseColor("#EF4444")
                overtraded -> Color.parseColor("#F5B041")
                else -> Color.parseColor("#9CA3AF")
            }
        )
    }

    private fun renderTradeCards(trades: List<TradeRecord>) {
        cardsContainer.removeAllViews()
        val ctx = context ?: return

        if (trades.isEmpty()) {
            val emptyTv = TextView(ctx).apply {
                text = "No trades logged in ${activeLedger.label} for this period."
                setTextColor(Color.parseColor("#6B7280"))
                textSize = 13f
                setPadding(12, 28, 12, 28)
            }
            cardsContainer.addView(emptyTv)
            return
        }

        val timeFmt = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.US)

        for (trade in trades) {
            val usdVal = computeTradeUsd(trade)
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#131722"))
                setPadding(28, 22, 28, 22)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 16
                layoutParams = params
            }

            // Header row: Direction + Status + Session + Edit Button
            val headerRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleTv = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val badgeColor = if (trade.direction == "BUY") "#10B981" else "#EF4444"
                setTextColor(Color.parseColor(badgeColor))
                textSize = 13.5f
                paint.isFakeBoldText = true
                text = "${trade.direction} ${trade.symbol}  •  ${trade.status}  •  ${trade.session.uppercase(Locale.US)}"
            }

            val btnEdit = TextView(ctx).apply {
                text = "EDIT"
                setTextColor(Color.parseColor("#F5B041"))
                setBackgroundColor(Color.parseColor("#1E2433"))
                textSize = 11f
                paint.isFakeBoldText = true
                setPadding(20, 8, 20, 8)
                setOnClickListener { showTradeEntryOrEditDialog(existingTrade = trade) }
            }

            headerRow.addView(titleTv)
            headerRow.addView(btnEdit)

            val pnlRow = TextView(ctx).apply {
                val pnlColor = when {
                    usdVal > 0.01 -> "#10B981"
                    usdVal < -0.01 -> "#EF4444"
                    else -> "#E5E7EB"
                }
                setTextColor(Color.parseColor(pnlColor))
                textSize = 14f
                paint.isFakeBoldText = true
                setPadding(0, 8, 0, 4)
                val levelsPart = if (trade.entry > 0.0) {
                    "  |  Entry ${trade.entry} • SL ${trade.sl} • TP ${trade.tp}"
                } else {
                    ""
                }
                text = String.format(Locale.US, "Net: $%+.2f (Lot %.2f)%s", usdVal, trade.lotSize, levelsPart)
            }

            val metaRow = TextView(ctx).apply {
                setTextColor(Color.parseColor("#8A94A6"))
                textSize = 11f
                val notePreview = if (trade.notes.isNotBlank()) " • ${trade.notes}" else ""
                text = "${timeFmt.format(Date(trade.effectiveTimestamp()))}$notePreview"
            }

            card.addView(headerRow)
            card.addView(pnlRow)
            card.addView(metaRow)

            card.setOnClickListener { showQuickActionDialog(trade) }
            cardsContainer.addView(card)
        }
    }

    private fun showQuickActionDialog(trade: TradeRecord) {
        val ctx = context ?: return
        val options = mutableListOf(
            "Edit Trade Details",
            "Mark TP Hit",
            "Mark SL Hit",
            "Mark Breakeven (BE)"
        )
        if (trade.ledger != TradeLedger.MANUAL) {
            options.add("Copy to My MT5 Journal")
        }
        options.add("Delete Trade")

        AlertDialog.Builder(ctx)
            .setTitle("${trade.direction} ${trade.symbol} (${trade.session})")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Edit Trade Details" -> showTradeEntryOrEditDialog(existingTrade = trade)
                    "Mark TP Hit" -> TradeHistoryManager.updateStatus(ctx, trade.id, "TP_HIT")
                    "Mark SL Hit" -> TradeHistoryManager.updateStatus(ctx, trade.id, "SL_HIT")
                    "Mark Breakeven (BE)" -> TradeHistoryManager.updateStatus(ctx, trade.id, "BE")
                    "Copy to My MT5 Journal" -> {
                        TradeHistoryManager.promoteToManualJournal(ctx, trade)
                        Toast.makeText(ctx, "Copied to My MT5 Journal", Toast.LENGTH_SHORT).show()
                    }
                    "Delete Trade" -> TradeHistoryManager.deleteTrade(ctx, trade.id)
                }
                refreshDashboard()
            }
            .show()
    }

    /**
     * 10-Second Quick-Log & Edit Sheet with big 1-tap buttons for Direction, Outcome, and Session.
     */
    private fun showTradeEntryOrEditDialog(existingTrade: TradeRecord?) {
        val ctx = context ?: return
        val scroll = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 28, 44, 16)
        }
        scroll.addView(container)

        var selectedDir = existingTrade?.direction ?: "BUY"
        var selectedOutcome = existingTrade?.status ?: "TP_HIT"
        var selectedSession = when {
            existingTrade == null -> "London"
            existingTrade.session.equals("Asian", true) -> "Asian"
            existingTrade.session.equals("London", true) -> "London"
            else -> "New York"
        }
        var selectedLot = existingTrade?.lotSize ?: 0.01

        fun createToggleRow(options: List<Pair<String, String>>, initialKey: String, onSelect: (String) -> Unit): LinearLayout {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = 16
                layoutParams = lp
            }
            val buttons = mutableListOf<Pair<TextView, String>>()
            for ((key, label) in options) {
                val tv = TextView(ctx).apply {
                    text = label
                    gravity = Gravity.CENTER
                    textSize = 12.5f
                    paint.isFakeBoldText = true
                    setPadding(12, 24, 12, 24)
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    lp.marginEnd = 8
                    layoutParams = lp
                }
                buttons.add(tv to key)
                row.addView(tv)
            }
            fun refreshButtons(active: String) {
                for ((tv, key) in buttons) {
                    if (key == active) {
                        val activeBg = when (key) {
                            "BUY", "TP_HIT" -> "#10B981"
                            "SELL", "SL_HIT" -> "#EF4444"
                            else -> "#F5B041"
                        }
                        tv.setBackgroundColor(Color.parseColor(activeBg))
                        tv.setTextColor(Color.parseColor("#0B0E14"))
                    } else {
                        tv.setBackgroundColor(Color.parseColor("#1E2433"))
                        tv.setTextColor(Color.parseColor("#D1D5DB"))
                    }
                }
            }
            refreshButtons(initialKey)
            for ((tv, key) in buttons) {
                tv.setOnClickListener {
                    refreshButtons(key)
                    onSelect(key)
                }
            }
            return row
        }

        fun addSectionLabel(text: String) {
            container.addView(TextView(ctx).apply {
                this.text = text
                setTextColor(Color.parseColor("#8A94A6"))
                textSize = 11f
                paint.isFakeBoldText = true
                setPadding(0, 6, 0, 6)
            })
        }

        // 1. Direction (BUY / SELL)
        addSectionLabel("1. DIRECTION")
        container.addView(
            createToggleRow(listOf("BUY" to "BUY", "SELL" to "SELL"), selectedDir) { selectedDir = it }
        )

        // 2. Outcome (WIN / LOSS / BE)
        addSectionLabel("2. OUTCOME")
        container.addView(
            createToggleRow(
                listOf("TP_HIT" to "WIN (+)", "SL_HIT" to "LOSS (-)", "BE" to "BE (0)"),
                selectedOutcome
            ) { selectedOutcome = it }
        )

        // 3. Session (3 Big Explicit Buttons: ASIA / LONDON / NEW YORK)
        addSectionLabel("3. SESSION (TAP TO VERIFY)")
        container.addView(
            createToggleRow(
                listOf("Asian" to "ASIA", "London" to "LONDON", "New York" to "NEW YORK"),
                selectedSession
            ) { selectedSession = it }
        )

        // 4. Net Profit/Loss in USD
        addSectionLabel("4. NET PROFIT / LOSS (USD)")
        val etUsd = EditText(ctx).apply {
            hint = "e.g. 25.50 (auto-applies - sign if LOSS)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            existingTrade?.pnlUsd?.let { setText(String.format(Locale.US, "%.2f", abs(it))) }
        }
        container.addView(etUsd)

        // 5. Quick Lot Size Chips
        addSectionLabel("5. LOT SIZE")
        val etLot = EditText(ctx).apply {
            hint = "Lot Size (default 0.01)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.2f", selectedLot))
        }
        container.addView(
            createToggleRow(
                listOf("0.01" to "0.01", "0.02" to "0.02", "0.05" to "0.05", "0.10" to "0.10"),
                String.format(Locale.US, "%.2f", selectedLot)
            ) { lotStr ->
                selectedLot = lotStr.toDoubleOrNull() ?: 0.01
                etLot.setText(lotStr)
            }
        )
        container.addView(etLot)

        // 6. Optional Notes & Price Levels
        val etNotes = EditText(ctx).apply {
            hint = "Setup Note (optional, e.g. London Sweep)"
            setText(existingTrade?.notes ?: "")
        }
        container.addView(etNotes)

        val optionalPricesBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (existingTrade != null && existingTrade.entry > 0.0) View.VISIBLE else View.GONE
        }
        val etEntry = EditText(ctx).apply {
            hint = "Entry Price (optional)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existingTrade != null && existingTrade.entry > 0.0) setText(existingTrade.entry.toString())
        }
        val etSl = EditText(ctx).apply {
            hint = "Stop Loss (optional)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existingTrade != null && existingTrade.sl > 0.0) setText(existingTrade.sl.toString())
        }
        val etTp = EditText(ctx).apply {
            hint = "Take Profit (optional)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existingTrade != null && existingTrade.tp > 0.0) setText(existingTrade.tp.toString())
        }
        optionalPricesBox.addView(etEntry)
        optionalPricesBox.addView(etSl)
        optionalPricesBox.addView(etTp)

        val tvTogglePrices = TextView(ctx).apply {
            text = if (optionalPricesBox.visibility == View.VISIBLE) "▲ Hide Entry / SL / TP" else "▼ Add Entry / SL / TP Prices (Optional)"
            setTextColor(Color.parseColor("#F5B041"))
            textSize = 12f
            setPadding(0, 14, 0, 10)
            setOnClickListener {
                if (optionalPricesBox.visibility == View.GONE) {
                    optionalPricesBox.visibility = View.VISIBLE
                    text = "▲ Hide Entry / SL / TP"
                } else {
                    optionalPricesBox.visibility = View.GONE
                    text = "▼ Add Entry / SL / TP Prices (Optional)"
                }
            }
        }
        container.addView(tvTogglePrices)
        container.addView(optionalPricesBox)

        val dialogTitle = if (existingTrade == null) "Quick-Log MT5 Trade" else "Edit Trade Record"
        val builder = AlertDialog.Builder(ctx)
            .setTitle(dialogTitle)
            .setView(scroll)
            .setPositiveButton("Save Trade") { _, _ ->
                val rawUsd = etUsd.text.toString().toDoubleOrNull()
                val signedUsd = when {
                    rawUsd == null -> if (selectedOutcome == "BE") 0.0 else null
                    selectedOutcome == "SL_HIT" -> -abs(rawUsd)
                    selectedOutcome == "TP_HIT" -> abs(rawUsd)
                    else -> rawUsd
                }
                val lot = etLot.text.toString().toDoubleOrNull() ?: 0.01
                val entry = etEntry.text.toString().toDoubleOrNull() ?: 0.0
                val sl = etSl.text.toString().toDoubleOrNull() ?: 0.0
                val tp = etTp.text.toString().toDoubleOrNull() ?: 0.0
                val notes = etNotes.text.toString().trim()

                if (existingTrade == null) {
                    val newRecord = TradeRecord(
                        ledger = activeLedger,
                        direction = selectedDir,
                        entry = entry,
                        sl = sl,
                        tp = tp,
                        lotSize = lot,
                        pnlUsd = signedUsd,
                        status = selectedOutcome,
                        session = selectedSession,
                        notes = notes,
                        closedAt = System.currentTimeMillis()
                    )
                    TradeHistoryManager.addTrade(ctx, newRecord)
                } else {
                    val updated = existingTrade.copy(
                        direction = selectedDir,
                        entry = entry,
                        sl = sl,
                        tp = tp,
                        lotSize = lot,
                        pnlUsd = signedUsd,
                        status = selectedOutcome,
                        session = selectedSession,
                        notes = notes,
                        closedAt = existingTrade.closedAt ?: System.currentTimeMillis()
                    )
                    TradeHistoryManager.updateTrade(ctx, updated)
                }
                refreshDashboard()
            }
            .setNegativeButton("Cancel", null)

        if (existingTrade != null) {
            builder.setNeutralButton("Delete") { _, _ ->
                TradeHistoryManager.deleteTrade(ctx, existingTrade.id)
                refreshDashboard()
            }
        }
        builder.show()
    }

    private fun showEditGoalsDialog() {
        val ctx = context ?: return
        val current = GoalsManager.getGoals(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 28, 48, 12)
        }

        val etDailyTarget = EditText(ctx).apply {
            hint = "Daily Profit Target (USD)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.0f", current.dailyProfitTargetUsd))
        }
        val etMonthlyTarget = EditText(ctx).apply {
            hint = "Monthly Profit Target (USD)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.0f", current.monthlyProfitTargetUsd))
        }
        val etMaxLoss = EditText(ctx).apply {
            hint = "Daily Max Loss Limit (USD)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.0f", current.dailyMaxLossUsd))
        }
        val etMaxTrades = EditText(ctx).apply {
            hint = "Max Trades Per Day"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.dailyMaxTrades.toString())
        }

        container.addView(TextView(ctx).apply { text = "Daily Profit Target ($)"; setTextColor(Color.GRAY) })
        container.addView(etDailyTarget)
        container.addView(TextView(ctx).apply { text = "Monthly Profit Target ($)"; setTextColor(Color.GRAY) })
        container.addView(etMonthlyTarget)
        container.addView(TextView(ctx).apply { text = "Daily Max Loss Guardrail ($)"; setTextColor(Color.GRAY) })
        container.addView(etMaxLoss)
        container.addView(TextView(ctx).apply { text = "Daily Max Trades Limit"; setTextColor(Color.GRAY) })
        container.addView(etMaxTrades)

        AlertDialog.Builder(ctx)
            .setTitle("Trading Goals & Risk Guardrails")
            .setView(container)
            .setPositiveButton("Save Goals") { _, _ ->
                GoalsManager.saveGoals(
                    ctx,
                    TradingGoals(
                        dailyProfitTargetUsd = etDailyTarget.text.toString().toDoubleOrNull() ?: current.dailyProfitTargetUsd,
                        monthlyProfitTargetUsd = etMonthlyTarget.text.toString().toDoubleOrNull() ?: current.monthlyProfitTargetUsd,
                        dailyMaxLossUsd = abs(etMaxLoss.text.toString().toDoubleOrNull() ?: current.dailyMaxLossUsd),
                        dailyMaxTrades = etMaxTrades.text.toString().toIntOrNull() ?: current.dailyMaxTrades
                    )
                )
                refreshDashboard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
