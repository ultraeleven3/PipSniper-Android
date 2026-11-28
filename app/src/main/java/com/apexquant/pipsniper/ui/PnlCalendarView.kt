package com.apexquant.pipsniper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

enum class CalendarScale {
    DAY, MONTH, YEAR
}

data class PeriodPnlSummary(
    val key: String, // "yyyy-MM-dd", "yyyy-MM", or "yyyy"
    val netPips: Double,
    val netUsd: Double,
    val hasUsd: Boolean,
    val tradeCount: Int,
    val wins: Int,
    val losses: Int
)

class PnlCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var scale: CalendarScale = CalendarScale.DAY
    private var year: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var month: Int = Calendar.getInstance().get(Calendar.MONTH) // 0-indexed
    private var summaries: Map<String, PeriodPnlSummary> = emptyMap()
    private var yearList: List<Int> = emptyList()
    private var selectedKey: String? = null

    var onDaySelected: ((String?) -> Unit)? = null
    var onMonthDrilled: ((Int) -> Unit)? = null
    var onYearDrilled: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val headerHeight = 26f * density
    private val cellGap = 5f * density
    private val cornerRadius = 8f * density
    private val cellRects = mutableListOf<Pair<RectF, String>>()

    private val paintHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A94A6")
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val paintCellBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paintCellBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private val paintTitleNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D1D5DB")
        textSize = 11f * density
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    private val paintPnlText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val paintSubText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9CA3AF")
        textSize = 9f * density
        textAlign = Paint.Align.CENTER
    }

    fun setCalendarData(
        targetScale: CalendarScale,
        targetYear: Int,
        targetMonth: Int,
        periodSummaries: Map<String, PeriodPnlSummary>,
        availableYears: List<Int>,
        selectedFilterKey: String?
    ) {
        this.scale = targetScale
        this.year = targetYear
        this.month = targetMonth
        this.summaries = periodSummaries
        this.yearList = availableYears
        this.selectedKey = selectedFilterKey
        requestLayout()
        invalidate()
    }

    private fun getDayRowCount(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOffset = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return ((firstDayOffset + daysInMonth + 6) / 7).coerceAtLeast(5)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val totalHeight = when (scale) {
            CalendarScale.DAY -> {
                val rows = getDayRowCount()
                val cellHeight = 52f * density
                (headerHeight + rows * (cellHeight + cellGap) + 6f * density).toInt()
            }
            CalendarScale.MONTH -> {
                val rows = 3 // 4 cols x 3 rows = 12 months
                val cellHeight = 68f * density
                (rows * (cellHeight + cellGap) + 6f * density).toInt()
            }
            CalendarScale.YEAR -> {
                val count = yearList.size.coerceAtLeast(1)
                val rows = (count + 2) / 3
                val cellHeight = 74f * density
                (rows * (cellHeight + cellGap) + 6f * density).toInt()
            }
        }
        setMeasuredDimension(width, totalHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cellRects.clear()
        when (scale) {
            CalendarScale.DAY -> drawDayGrid(canvas)
            CalendarScale.MONTH -> drawMonthGrid(canvas)
            CalendarScale.YEAR -> drawYearGrid(canvas)
        }
    }

    private fun drawDayGrid(canvas: Canvas) {
        val daysOfWeek = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        val colWidth = (width - cellGap * 6f) / 7f

        for (col in 0 until 7) {
            val cx = col * (colWidth + cellGap) + colWidth / 2f
            canvas.drawText(daysOfWeek[col], cx, headerHeight * 0.72f, paintHeader)
        }

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOffset = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        val cellHeight = 52f * density

        for (day in 1..daysInMonth) {
            val index = firstDayOffset + (day - 1)
            val row = index / 7
            val col = index % 7

            val left = col * (colWidth + cellGap)
            val top = headerHeight + row * (cellHeight + cellGap)
            val rect = RectF(left, top, left + colWidth, top + cellHeight)
            val dayKey = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
            cellRects.add(rect to dayKey)

            drawHeatmapCell(
                canvas = canvas,
                rect = rect,
                topLeftLabel = day.toString(),
                isCurrentPeriod = (dayKey == todayKey),
                isSelected = (dayKey == selectedKey),
                summary = summaries[dayKey],
                showWinRate = false
            )
        }
    }

    private fun drawMonthGrid(canvas: Canvas) {
        val monthNames = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        val cols = 4
        val colWidth = (width - cellGap * (cols - 1)) / cols.toFloat()
        val cellHeight = 68f * density
        val now = Calendar.getInstance()
        val currentMonthKey = String.format(Locale.US, "%04d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)

        for (m in 0 until 12) {
            val row = m / cols
            val col = m % cols
            val left = col * (colWidth + cellGap)
            val top = row * (cellHeight + cellGap)
            val rect = RectF(left, top, left + colWidth, top + cellHeight)
            val monthKey = String.format(Locale.US, "%04d-%02d", year, m + 1)
            cellRects.add(rect to monthKey)

            drawHeatmapCell(
                canvas = canvas,
                rect = rect,
                topLeftLabel = monthNames[m],
                isCurrentPeriod = (monthKey == currentMonthKey),
                isSelected = false,
                summary = summaries[monthKey],
                showWinRate = true
            )
        }
    }

    private fun drawYearGrid(canvas: Canvas) {
        val years = if (yearList.isEmpty()) listOf(year) else yearList
        val cols = 3
        val colWidth = (width - cellGap * (cols - 1)) / cols.toFloat()
        val cellHeight = 74f * density
        val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()

        years.forEachIndexed { idx, yr ->
            val row = idx / cols
            val col = idx % cols
            val left = col * (colWidth + cellGap)
            val top = row * (cellHeight + cellGap)
            val rect = RectF(left, top, left + colWidth, top + cellHeight)
            val yrKey = yr.toString()
            cellRects.add(rect to yrKey)

            drawHeatmapCell(
                canvas = canvas,
                rect = rect,
                topLeftLabel = yrKey,
                isCurrentPeriod = (yrKey == currentYear),
                isSelected = false,
                summary = summaries[yrKey],
                showWinRate = true
            )
        }
    }

    private fun drawHeatmapCell(
        canvas: Canvas,
        rect: RectF,
        topLeftLabel: String,
        isCurrentPeriod: Boolean,
        isSelected: Boolean,
        summary: PeriodPnlSummary?,
        showWinRate: Boolean
    ) {
        val metricValue = summary?.let { if (it.hasUsd) it.netUsd else it.netPips } ?: 0.0

        paintCellBg.color = when {
            summary == null || summary.tradeCount == 0 -> Color.parseColor("#131722")
            metricValue > 0.01 -> Color.parseColor("#103829")
            metricValue < -0.01 -> Color.parseColor("#3B161E")
            else -> Color.parseColor("#1E2433")
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paintCellBg)

        when {
            isSelected -> {
                paintCellBorder.color = Color.parseColor("#F5B041")
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paintCellBorder)
            }
            isCurrentPeriod -> {
                paintCellBorder.color = Color.parseColor("#4B5563")
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paintCellBorder)
            }
        }

        paintTitleNum.color = if (isCurrentPeriod) Color.parseColor("#F5B041") else Color.parseColor("#9CA3AF")
        canvas.drawText(topLeftLabel, rect.left + 6f * density, rect.top + 14f * density, paintTitleNum)

        if (summary != null && summary.tradeCount > 0) {
            val pnlLabel = if (summary.hasUsd) {
                val sign = if (summary.netUsd >= 0) "+" else "-"
                "$sign$${String.format(Locale.US, "%.0f", abs(summary.netUsd))}"
            } else {
                val sign = if (summary.netPips >= 0) "+" else "-"
                "$sign${String.format(Locale.US, "%.0f", abs(summary.netPips))}p"
            }

            paintPnlText.color = when {
                metricValue > 0.01 -> Color.parseColor("#10B981")
                metricValue < -0.01 -> Color.parseColor("#EF4444")
                else -> Color.parseColor("#D1D5DB")
            }
            canvas.drawText(pnlLabel, rect.centerX(), rect.top + rect.height() * 0.62f, paintPnlText)

            val subLabel = if (showWinRate) {
                val decided = summary.wins + summary.losses
                val wr = if (decided > 0) (summary.wins * 100 / decided) else 0
                "${summary.tradeCount}T • ${wr}%W"
            } else {
                "${summary.tradeCount}T"
            }
            canvas.drawText(subLabel, rect.centerX(), rect.bottom - 6f * density, paintSubText)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            for ((rect, key) in cellRects) {
                if (rect.contains(x, y)) {
                    when (scale) {
                        CalendarScale.DAY -> {
                            selectedKey = if (selectedKey == key) null else key
                            invalidate()
                            onDaySelected?.invoke(selectedKey)
                        }
                        CalendarScale.MONTH -> {
                            val monthNum = key.substringAfter("-").toIntOrNull()
                            if (monthNum != null) onMonthDrilled?.invoke(monthNum - 1)
                        }
                        CalendarScale.YEAR -> {
                            val yrNum = key.toIntOrNull()
                            if (yrNum != null) onYearDrilled?.invoke(yrNum)
                        }
                    }
                    return true
                }
            }
        }
        return true
    }
}
