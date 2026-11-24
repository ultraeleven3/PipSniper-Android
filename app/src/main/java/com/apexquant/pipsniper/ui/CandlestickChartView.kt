package com.apexquant.pipsniper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.apexquant.pipsniper.core.Candle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class CandlestickChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var candles: List<Candle> = emptyList()
    private var entryLevel: Double? = null
    private var slLevel: Double? = null
    private var tpLevel: Double? = null

    private val bullPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF10B981.toInt()
        strokeWidth = 3f
    }
    private val bearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEF4444.toInt()
        strokeWidth = 3f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E2530.toInt()
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt()
        textSize = 26f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    fun updateData(newCandles: List<Candle>, entry: Double? = null, sl: Double? = null, tp: Double? = null) {
        candles = newCandles.takeLast(35)
        entryLevel = entry?.takeIf { it > 0.0 }
        slLevel = sl?.takeIf { it > 0.0 }
        tpLevel = tp?.takeIf { it > 0.0 }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF141920.toInt())

        if (candles.isEmpty()) {
            canvas.drawText("Loading live Exness XAUUSD chart...", 36f, height / 2f, textPaint)
            return
        }

        val padTop = 36f
        val padBottom = 36f
        val padLeft = 20f
        val padRight = 135f
        val chartW = width - padLeft - padRight
        val chartH = height - padTop - padBottom

        var maxPrice = candles.maxOf { it.high }
        var minPrice = candles.minOf { it.low }

        listOfNotNull(entryLevel, slLevel, tpLevel).forEach { lvl ->
            if (absDiffReasonable(lvl, maxPrice)) {
                maxPrice = max(maxPrice, lvl)
                minPrice = min(minPrice, lvl)
            }
        }

        val range = max(maxPrice - minPrice, 0.50)
        fun priceToY(p: Double): Float = padTop + ((maxPrice - p) / range * chartH).toFloat()

        // Horizontal grid & price axis labels
        for (i in 0..3) {
            val y = padTop + (chartH / 3f) * i
            canvas.drawLine(padLeft, y, width - padRight, y, gridPaint)
            val priceAtY = maxPrice - (range / 3.0) * i
            canvas.drawText(String.format(Locale.US, "%.2f", priceAtY), width - padRight + 10f, y + 8f, textPaint)
        }

        // Draw candles
        val step = chartW / candles.size.toFloat()
        val bodyW = max(step * 0.62f, 4f)

        candles.forEachIndexed { idx, c ->
            val cx = padLeft + step * idx + step / 2f
            val isBull = c.close >= c.open
            val paint = if (isBull) bullPaint else bearPaint

            canvas.drawLine(cx, priceToY(c.high), cx, priceToY(c.low), paint)
            val top = priceToY(max(c.open, c.close))
            val bottom = max(priceToY(min(c.open, c.close)), top + 3f)
            canvas.drawRect(cx - bodyW / 2f, top, cx + bodyW / 2f, bottom, paint)
        }

        // Draw Entry, SL, TP overlays
        drawLevel(canvas, entryLevel, 0xFFD4AF37.toInt(), "ENTRY", padLeft, width - padRight, ::priceToY)
        drawLevel(canvas, slLevel, 0xFFEF4444.toInt(), "SL", padLeft, width - padRight, ::priceToY)
        drawLevel(canvas, tpLevel, 0xFF10B981.toInt(), "TP", padLeft, width - padRight, ::priceToY)
    }

    private fun drawLevel(
        canvas: Canvas,
        level: Double?,
        color: Int,
        tag: String,
        left: Float,
        right: Float,
        priceToY: (Double) -> Float
    ) {
        if (level == null) return
        val y = priceToY(level)
        if (y < 10f || y > height - 10f) return
        linePaint.color = color
        textPaint.color = color
        canvas.drawLine(left, y, right, y, linePaint)
        canvas.drawText("$tag ${String.format(Locale.US, "%.1f", level)}", left + 8f, y - 6f, textPaint)
        textPaint.color = 0xFF94A3B8.toInt()
    }

    private fun absDiffReasonable(level: Double, ref: Double): Boolean =
        kotlin.math.abs(level - ref) < ref * 0.05
}
