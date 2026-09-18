package com.example.wifidataguard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.max

/**
 * 7-day usage bar chart (spec-010 FR-104). One bar per local day: rounded
 * top, today outlined in accent, over-budget days red, days with data but
 * no baseline (null) dashed-empty — never a fake number. Purely cosmetic.
 */
class HistoryBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Day(
        val epochDay: Long,
        val usedBytes: Long?,   // null = data but no baseline
        val label: String,
        val isToday: Boolean
    )

    /** Mirrors GlassUi.animationsEnabled() — set by the activity. */
    var animationsEnabled = true

    private var days: List<Day> = emptyList()
    private var budget = 0L
    private var maxBytes = 1L
    private var growFraction = 1f
    private var animator: ValueAnimator? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x2EFFFFFF
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5A6684.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A96B8.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()
    private val dashEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)

    fun setData(newDays: List<Day>, dayBudget: Long) {
        val hadData = days.isNotEmpty()
        days = newDays
        budget = dayBudget
        maxBytes = max(1L, (newDays.maxOfOrNull { it.usedBytes ?: 0L }) ?: 1L)
        if (animationsEnabled && !hadData && newDays.isNotEmpty()) {
            growFraction = 0f
            animator?.cancel()
            val a = ValueAnimator.ofFloat(0f, 1f)
            a.duration = 700
            a.interpolator = DecelerateInterpolator()
            a.addUpdateListener { anim ->
                growFraction = anim.animatedValue as Float
                invalidate()
            }
            a.start()
            animator = a
        } else {
            growFraction = 1f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || days.isEmpty()) {
            drawEmptyHint(canvas, d)
            return
        }
        val labelH = 18f * d
        val valueH = 14f * d
        val chartH = h - labelH - valueH - 6f * d
        val n = days.size
        val gap = 8f * d
        val barW = (w - gap * (n - 1)) / n
        val corner = minOf(6f * d, barW / 2f)

        labelPaint.textSize = 11f * d
        valuePaint.textSize = 10f * d

        for ((i, day) in days.withIndex()) {
            val left = i * (barW + gap)
            val cx = left + barW / 2f

            val used = day.usedBytes
            if (used == null) {
                // data but no baseline: dashed empty bar (honest "unknown")
                trackPaint.pathEffect = dashEffect
                trackPaint.strokeWidth = 1.5f * d
                val dh = 14f * d
                rect.set(left + 2f * d, chartH - dh, left + barW - 2f * d, chartH)
                canvas.drawRoundRect(rect, corner, corner, trackPaint)
                trackPaint.pathEffect = null
            } else {
                val frac = used.toFloat() / maxBytes
                val fullH = max(4f * d, frac * chartH * growFraction)
                val over = budget > 0 && used >= budget
                barPaint.color = when {
                    over -> 0xFFF87171.toInt()
                    day.isToday -> 0xFF22D3EE.toInt()
                    else -> 0xFF7C8CFF.toInt()
                }
                rect.set(left, chartH - fullH, left + barW, chartH + valueH + labelH)
                canvas.save()
                canvas.clipRect(0f, 0f, w, chartH + 0.5f)   // square bottoms
                canvas.drawRoundRect(rect, corner, corner, barPaint)
                canvas.restore()
                // today outline glow
                if (day.isToday) {
                    trackPaint.pathEffect = null
                    trackPaint.strokeWidth = 1.5f * d
                    trackPaint.color = 0x66FFFFFF
                    rect.set(left, chartH - fullH, left + barW, chartH)
                    canvas.drawRoundRect(rect, corner, corner, trackPaint)
                    trackPaint.color = 0x2EFFFFFF
                }
                val vLabel = HistoryUi.compact(used)
                if (vLabel.isNotEmpty() && fullH > 18f * d) {
                    canvas.drawText(vLabel, cx, chartH - fullH - 4f * d, valuePaint)
                }
            }
            // weekday label
            labelPaint.color = if (day.isToday) 0xFF22D3EE.toInt() else 0xFF5A6684.toInt()
            canvas.drawText(day.label, cx, h - 2f * d, labelPaint)
        }
    }

    private fun drawEmptyHint(canvas: Canvas, d: Float) {
        labelPaint.textSize = 12f * d
        labelPaint.color = 0xFF5A6684.toInt()
        labelPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("· · ·", width / 2f, height / 2f, labelPaint)
        labelPaint.textAlign = Paint.Align.CENTER
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
