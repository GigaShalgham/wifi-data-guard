package com.example.wifidataguard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/**
 * Animated usage ring (spec-010 FR-103). Purely presentational — it draws
 * the SAME truth the old flat progress bar drew (used/limit + guard state),
 * so Art. VIII is untouched: no enforcement, timing or state logic lives
 * here. Animations snap instantly under reduce-motion.
 */
class UsageRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Mirrors GlassUi.animationsEnabled() — set by the activity. */
    var animationsEnabled = true

    private var displayedPct = 0f          // what is drawn (animated)
    private var targetPct = 0f
    private var usedBytes = 0L
    private var limitBytes = 0L
    private var state = GuardStateUi.S.PROTECTED
    private var animator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x1AFFFFFF
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A96B8.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    private val strokePx: Float
    private val dotRadiusPx: Float

    init {
        val d = resources.displayMetrics.density
        strokePx = 13f * d
        dotRadiusPx = 5f * d
        trackPaint.strokeWidth = strokePx
        arcPaint.strokeWidth = strokePx
        arcPaint.strokeCap = Paint.Cap.ROUND
    }

    /** Feed the same truth the status line renders. Animates only when the
     *  target moves by >= 1 pct (the 1 s tick with unchanged usage snaps). */
    fun setUsage(used: Long, limit: Long, s: GuardStateUi.S) {
        val changed = used != usedBytes || limit != limitBytes || s != state
        usedBytes = used
        limitBytes = limit
        state = s
        targetPct = if (limit > 0) (used * 100f / limit).coerceIn(0f, 100f) else 0f
        if (changed) {
            if (animationsEnabled && kotlin.math.abs(targetPct - displayedPct) >= 1f) {
                animateTo(targetPct)
            } else {
                displayedPct = targetPct
                invalidate()
            }
        }
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        val a = ValueAnimator.ofFloat(displayedPct, target)
        a.duration = 800
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { anim ->
            displayedPct = anim.animatedValue as Float
            invalidate()
        }
        a.start()
        animator = a
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = (size - strokePx - dotRadiusPx * 4) / 2f
        if (radius <= 0f) return

        // track
        trackPaint.strokeWidth = strokePx
        canvas.drawCircle(cx, cy, radius, trackPaint)

        // sweep gradient arc (starts at 12 o'clock)
        val (c0, c1) = when {
            state == GuardStateUi.S.GRACE -> 0xFFFFB300.toInt() to 0xFFFCD34D.toInt()
            targetPct >= 100f -> 0xFFFF5449.toInt() to 0xFFF87171.toInt()
            targetPct >= 80f -> 0xFFFFB300.toInt() to 0xFFF97316.toInt()
            else -> 0xFF34D399.toInt() to 0xFF22D3EE.toInt()
        }
        arcPaint.shader = SweepGradient(cx, cy, intArrayOf(c0, c1, c0), null)
        arcPaint.strokeWidth = strokePx
        if (displayedPct > 0f) {
            canvas.save()
            canvas.rotate(-90f, cx, cy)
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(rect, 0f, displayedPct * 3.6f, false, arcPaint)
            canvas.restore()
        }

        // center: big % + used/limit
        val d = resources.displayMetrics.density
        pctPaint.textSize = 44f * d
        subPaint.textSize = 14f * d
        val pctText = if (limitBytes > 0) "${targetPct.toInt()}%" else "—"
        val subText = if (limitBytes > 0)
            "${fmt(usedBytes)} / ${fmt(limitBytes)}" else "—"
        val bigY = cy + 6f * d
        canvas.drawText(pctText, cx, bigY, pctPaint)
        canvas.drawText(subText, cx, bigY + 24f * d, subPaint)
    }

    private fun fmt(b: Long): String =
        if (b >= 1_073_741_824L) String.format(java.util.Locale.US, "%.2f GB", b / 1_073_741_824.0)
        else String.format(java.util.Locale.US, "%.0f MB", b / 1_048_576.0)

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
