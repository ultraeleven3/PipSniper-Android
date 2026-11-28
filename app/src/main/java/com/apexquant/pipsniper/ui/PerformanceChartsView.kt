package com.apexquant.pipsniper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.apexquant.pipsniper.core.TradeRecord
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PerformanceChartsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var equityPoints: List<Double> = emptyList()
    private var sessionPnl: Map<String, Double> = emptyMap()
    private var sessionCounts: Map<String, Int> = emptyMap()
    private val density = resources.displayMetrics.density

    private val paintSectionTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A94A6")
        textSize = 10.5f * density
        isFakeBoldText = true
    }

    private val paintZeroLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A3142")
        strokeWidth = 1f * density
    }

    private val paintCurveLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5B041")
        strokeWidth = 2.5f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val paintCurveFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paintBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paintBarBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#131722")
        style = Paint.Style.FILL
    }

    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D1D5DB")
        textSize = 11f * density
        isFakeBoldText = true
    }

    private val paintValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * density
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
    }

    fun setTrades(trades: List<TradeRecord>) {
        val chronological = trades
            .filter { it.status in listOf("TP_HIT", "SL_HIT", "BE") || it.pnlUsd != null }
            .sortedBy { it.effectiveTimestamp() }

        val curve = mutableListOf(0.0)
        var running = 0.0
        for (t in chronological) {
            val delta = t.pnlUsd ?: (t.realizedPips() * t.lotSize * 10.0)
            running += delta
            curve.add(running)
        }
        equityPoints = curve

        val sessions = listOf("Asian", "London", "New York")
        val pnlMap = mutableMapOf<String, Double>()
        val countMap = mutableMapOf<String, Int>()
        for (s in sessions) {
            val matching = chronological.filter {
                if (s == "New York") it.session.equals("New York", true) || it.session.equals("Late NY", true)
                else it.session.equals(s, true)
            }
            pnlMap[s] = matching.sumOf { it.pnlUsd ?: (it.realizedPips() * it.lotSize * 10.0) }
            countMap[s] = matching.size
        }
        sessionPnl = pnlMap
        sessionCounts = countMap
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (255f * density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 10f * density
        val chartLeft = pad
        val chartRight = width - pad

        // 1. Cumulative Equity Curve (Top Half)
        canvas.drawText("CUMULATIVE EQUITY CURVE (USD)", chartLeft, 16f * density, paintSectionTitle)

        val curveTop = 26f * density
        val curveBottom = 122f * density
        val curveHeight = curveBottom - curveTop

        if (equityPoints.size < 2) {
            canvas.drawLine(chartLeft, curveTop + curveHeight / 2f, chartRight, curveTop + curveHeight / 2f, paintZeroLine)
            paintLabel.color = Color.parseColor("#6B7280")
            canvas.drawText("Log closed trades to plot equity growth", chartLeft, curveTop + curveHeight / 2f - 6f * density, paintLabel)
        } else {
            val minVal = min(0.0, equityPoints.minOrNull() ?: 0.0)
            val maxVal = max(10.0, equityPoints.maxOrNull() ?: 10.0)
            val span = (maxVal - minVal).coerceAtLeast(1.0)

            val zeroY = curveBottom - ((0.0 - minVal) / span).toFloat() * curveHeight
            canvas.drawLine(chartLeft, zeroY, chartRight, zeroY, paintZeroLine)

            val linePath = Path()
            val fillPath = Path()
            val stepX = (chartRight - chartLeft) / (equityPoints.size - 1).coerceAtLeast(1)

            equityPoints.forEachIndexed { i, value ->
                val x = chartLeft + i * stepX
                val y = curveBottom - ((value - minVal) / span).toFloat() * curveHeight
                if (i == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, zeroY)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (i == equityPoints.lastIndex) {
                    fillPath.lineTo(x, zeroY)
                    fillPath.close()
                }
            }

            paintCurveFill.shader = LinearGradient(
                0f, curveTop, 0f, curveBottom,
                Color.parseColor("#44F5B041"),
                Color.parseColor("#00F5B041"),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(fillPath, paintCurveFill)
            canvas.drawPath(linePath, paintCurveLine)

            val lastVal = equityPoints.last()
            val lastY = curveBottom - ((lastVal - minVal) / span).toFloat() * curveHeight
            paintDot.color = if (lastVal >= 0) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
            canvas.drawCircle(chartRight, lastY, 4f * density, paintDot)

            paintValue.color = paintDot.color
            canvas.drawText(String.format(Locale.US, "$%+.2f", lastVal), chartRight, 16f * density, paintValue)
        }

        // 2. Session Performance Bars (Bottom Half)
        val sessionTitleY = 148f * density
        canvas.drawText("SESSION NET P&L (ASIA / LONDON / NEW YORK)", chartLeft, sessionTitleY, paintSectionTitle)

        val sessions = listOf("Asian" to "ASIA", "London" to "LONDON", "New York" to "NEW YORK")
        val maxAbsSession = sessions.maxOf { abs(sessionPnl[it.first] ?: 0.0) }.coerceAtLeast(10.0)
        val barStartX = chartLeft + 92f * density
        val barEndX = chartRight - 64f * density
        val maxBarWidth = (barEndX - barStartX).coerceAtLeast(40f)
        val rowHeight = 28f * density

        sessions.forEachIndexed { idx, (key, label) ->
            val yTop = sessionTitleY + 12f * density + idx * rowHeight
            val yMid = yTop + 12f * density
            val valUsd = sessionPnl[key] ?: 0.0
            val count = sessionCounts[key] ?: 0

            paintLabel.color = Color.parseColor("#D1D5DB")
            canvas.drawText("$label ($count)", chartLeft, yMid + 4f * density, paintLabel)

            // Track background
            val bgRect = RectF(barStartX, yTop + 4f * density, barEndX, yTop + 18f * density)
            canvas.drawRoundRect(bgRect, 4f * density, 4f * density, paintBarBg)

            // Filled bar
            val ratio = (abs(valUsd) / maxAbsSession).toFloat().coerceIn(0f, 1f)
            if (ratio > 0.01f) {
                paintBar.color = if (valUsd >= 0) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
                val fillRect = RectF(barStartX, yTop + 4f * density, barStartX + maxBarWidth * ratio, yTop + 18f * density)
                canvas.drawRoundRect(fillRect, 4f * density, 4f * density, paintBar)
            }

            paintValue.color = when {
                valUsd > 0.01 -> Color.parseColor("#10B981")
                valUsd < -0.01 -> Color.parseColor("#EF4444")
                else -> Color.parseColor("#9CA3AF")
            }
            canvas.drawText(String.format(Locale.US, "$%+.0f", valUsd), chartRight, yMid + 4f * density, paintValue)
        }
    }
}
