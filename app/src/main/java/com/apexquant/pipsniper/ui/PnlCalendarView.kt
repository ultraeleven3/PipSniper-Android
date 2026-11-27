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

data class DayPnlSummary(
    val dayKey: String,
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

    private var year: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var month: Int = Calendar.getInstance().get(Calendar.MONTH) // 0-indexed
    private var daySummaries: Map<String, DayPnlSummary> = emptyMap()
    private var selectedDayKey: String? = null
    var onDaySelected: ((String?) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val headerHeight = 28f * density
    private val cellGap = 4f * density
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

    private val paintDayNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    private val paintCountText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9CA3AF")
        textSize = 9f * density
        textAlign = Paint.Align.CENTER
    }

    fun setMonthData(
        targetYear: Int,
        targetMonth: Int,
        summaries: Map<String, DayPnlSummary>,
        selectedDay: String?
    ) {
        this.year = targetYear
        this.month = targetMonth
        this.daySummaries = summaries
        this.selectedDayKey = selectedDay
        requestLayout()
        invalidate()
    }

    private fun getRowCount(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOffset = cal.get(Calendar.DAY_OF_WEEK) - 1 // Sunday = 0
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return ((firstDayOffset + daysInMonth + 6) / 7).coerceAtLeast(5)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = getRowCount()
        val cellHeight = 52f * density
        val totalHeight = (headerHeight + rows * (cellHeight + cellGap) + 8f * density).toInt()
        setMeasuredDimension(width, totalHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cellRects.clear()

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

            val summary = daySummaries[dayKey]
            val metricValue = summary?.let { if (it.hasUsd) it.netUsd else it.netPips } ?: 0.0

            // Cell background color based on daily profit/loss
            paintCellBg.color = when {
                summary == null || summary.tradeCount == 0 -> Color.parseColor("#131722")
                metricValue > 0.01 -> Color.parseColor("#103829") // Institutional Emerald
                metricValue < -0.01 -> Color.parseColor("#3B161E") // Deep Crimson
                else -> Color.parseColor("#1E2433") // Breakeven / Scanning
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paintCellBg)

            // Border highlight for Selected Day or Today
            when {
                dayKey == selectedDayKey -> {
                    paintCellBorder.color = Color.parseColor("#F5B041") // Gold active selection
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paintCellBorder)
                }
                dayKey == todayKey -> {
                    paintCellBorder.color = Color.parseColor("#4B5563")
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paintCellBorder)
                }
            }

            // Day number top-left
            paintDayNum.color = if (dayKey == todayKey) Color.parseColor("#F5B041") else Color.parseColor("#9CA3AF")
            canvas.drawText(day.toString(), left + 6f * density, top + 14f * density, paintDayNum)

            // Daily P&L & trade count
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
                canvas.drawText(pnlLabel, rect.centerX(), top + 32f * density, paintPnlText)
                canvas.drawText("${summary.tradeCount}T", rect.centerX(), top + 45f * density, paintCountText)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            for ((rect, key) in cellRects) {
                if (rect.contains(x, y)) {
                    selectedDayKey = if (selectedDayKey == key) null else key
                    invalidate()
                    onDaySelected?.invoke(selectedDayKey)
                    return true
                }
            }
        }
        return true
    }
}
