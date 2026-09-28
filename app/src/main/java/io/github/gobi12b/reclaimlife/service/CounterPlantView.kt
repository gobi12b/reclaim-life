package io.github.gobi12b.reclaimlife.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import io.github.gobi12b.reclaimlife.data.Mood
import kotlin.math.cos
import kotlin.math.sin

/**
 * The little potted plant beside the reel count in the floating counter. It sways gently; the
 * pot's face follows the same moods as the rest of the app (fresh, fine, getting close, winding
 * down, done), and near the limit the leaves droop and fade like it needs water. Never crying,
 * never red. A plain View, since the overlay window has no Compose lifecycle.
 */
internal class CounterPlantView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.2f * density
    }
    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    private var phase = 0f
    private var droop = 0f
    private var droopTarget = 0f
    private var mood = Mood.ENERGIZED
    private val potPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.3f * density
        color = FACE
    }

    private val clock = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            // Ease toward the target droop a little each frame, so changes read as a slow sigh.
            droop += (droopTarget - droop) * 0.04f
            invalidate()
        }
    }

    /** [count] of [limit] used: sets the face's mood, and the droop from half-way to the limit. */
    fun setProgress(count: Int, limit: Int) {
        val fraction = if (limit > 0) count.toFloat() / limit else 0f
        mood = Mood.forProgress(count, limit)
        droopTarget = ((fraction - 0.5f) / 0.5f).coerceIn(0f, 1f)
        if (!clock.isStarted) droop = droopTarget
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationsOn()) clock.start()
    }

    override fun onDetachedFromWindow() {
        clock.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow && animationsOn()) clock.start() else clock.cancel()
    }

    private fun animationsOn(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((24 * density).toInt(), (34 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val baseX = w / 2
        // The pot takes the bottom; the plant grows out of its rim.
        val potTop = h - 13 * density
        val baseY = potTop + 1 * density
        val sway = 4f * sin(phase * 2 * Math.PI).toFloat() * (1f - 0.6f * droop) - 8f * droop
        val topX = baseX + sway * 0.35f * density
        val topY = baseY - (h - 13 * density) * (0.78f - 0.08f * droop)

        stemPaint.color = ColorUtils.blendARGB(STEM, DRY, 0.3f * droop)
        path.reset()
        path.moveTo(baseX, baseY)
        path.quadTo(baseX - 2 * density, (baseY + topY) / 2, topX, topY)
        canvas.drawPath(path, stemPaint)

        // Two leaves at the top: upright when fresh, lowered toward horizontal when thirsty.
        val angle = 50f + 45f * droop
        val flutter = 5f * sin(phase * 4 * Math.PI).toFloat() * (1f - 0.7f * droop)
        drawLeaf(canvas, topX, topY, -angle + flutter, ColorUtils.blendARGB(LEAF, DRY, 0.35f * droop))
        drawLeaf(canvas, topX, topY, angle - flutter, ColorUtils.blendARGB(LEAF_BRIGHT, DRY, 0.35f * droop))
        drawPot(canvas, w, h, potTop)
    }

    /** A little terracotta pot with the face. Blinks now and then unless it's asleep. */
    private fun drawPot(canvas: Canvas, w: Float, h: Float, top: Float) {
        val d = density
        potPaint.color = POT
        path.reset()
        path.moveTo(w / 2 - 9 * d, top + 2 * d)
        path.lineTo(w / 2 + 9 * d, top + 2 * d)
        path.lineTo(w / 2 + 7 * d, h - 0.5f * d)
        path.lineTo(w / 2 - 7 * d, h - 0.5f * d)
        path.close()
        canvas.drawPath(path, potPaint)
        potPaint.color = POT_RIM
        canvas.drawRoundRect(w / 2 - 10 * d, top, w / 2 + 10 * d, top + 3 * d, 1.5f * d, 1.5f * d, potPaint)

        val cx = w / 2
        val eyeY = top + 6.5f * d
        val mouthY = top + 9.5f * d
        val blink = mood != Mood.DONE && (phase % 0.5f) in 0.46f..0.5f
        facePaint.style = Paint.Style.STROKE
        when {
            mood == Mood.DONE || mood == Mood.TIRED || blink -> {
                // Closed or heavy-lidded eyes: little downward arcs.
                listOf(-1f, 1f).forEach { side ->
                    path.reset()
                    path.moveTo(cx + side * 3.4f * d - 1.3f * d, eyeY)
                    path.quadTo(cx + side * 3.4f * d, eyeY + (if (mood == Mood.TIRED && !blink) 0.6f else 1.2f) * d, cx + side * 3.4f * d + 1.3f * d, eyeY)
                    canvas.drawPath(path, facePaint)
                }
            }
            else -> {
                facePaint.style = Paint.Style.FILL
                canvas.drawCircle(cx - 3.4f * d, eyeY, 0.95f * d, facePaint)
                canvas.drawCircle(cx + 3.4f * d, eyeY, 0.95f * d, facePaint)
                facePaint.style = Paint.Style.STROKE
            }
        }
        if (mood == Mood.UNEASY) {
            // Slightly raised inner brows: a little worried, not upset.
            canvas.drawLine(cx - 4.6f * d, eyeY - 2.2f * d, cx - 2.2f * d, eyeY - 2.8f * d, facePaint)
            canvas.drawLine(cx + 4.6f * d, eyeY - 2.2f * d, cx + 2.2f * d, eyeY - 2.8f * d, facePaint)
        }
        path.reset()
        when (mood) {
            Mood.ENERGIZED -> { path.moveTo(cx - 2.6f * d, mouthY - 0.6f * d); path.quadTo(cx, mouthY + 2.4f * d, cx + 2.6f * d, mouthY - 0.6f * d) }
            Mood.GOOD -> { path.moveTo(cx - 2f * d, mouthY - 0.3f * d); path.quadTo(cx, mouthY + 1.4f * d, cx + 2f * d, mouthY - 0.3f * d) }
            Mood.UNEASY -> { path.moveTo(cx - 2f * d, mouthY + 0.4f * d); path.quadTo(cx - 1f * d, mouthY - 0.4f * d, cx, mouthY + 0.4f * d); path.quadTo(cx + 1f * d, mouthY + 1.2f * d, cx + 2f * d, mouthY + 0.4f * d) }
            Mood.TIRED -> { path.moveTo(cx - 1.6f * d, mouthY + 0.5f * d); path.lineTo(cx + 1.6f * d, mouthY + 0.5f * d) }
            Mood.DONE -> path.addCircle(cx, mouthY + 0.4f * d, 0.9f * d, Path.Direction.CW)
        }
        canvas.drawPath(path, facePaint)
        if (mood == Mood.ENERGIZED) {
            // Rosy cheeks when things are going well.
            potPaint.color = CHEEK
            canvas.drawCircle(cx - 5.4f * d, mouthY - 0.6f * d, 1.2f * d, potPaint)
            canvas.drawCircle(cx + 5.4f * d, mouthY - 0.6f * d, 1.2f * d, potPaint)
        }
    }

    private fun drawLeaf(canvas: Canvas, x: Float, y: Float, degrees: Float, color: Int) {
        val len = 9.5f * density
        val theta = Math.toRadians(degrees.toDouble())
        val tipX = x + sin(theta).toFloat() * len
        val tipY = y - cos(theta).toFloat() * len
        val nx = -(tipY - y) / len * len * 0.21f
        val ny = (tipX - x) / len * len * 0.21f
        path.reset()
        path.moveTo(x, y)
        path.cubicTo(x + (tipX - x) * 0.25f + nx, y + (tipY - y) * 0.25f + ny, x + (tipX - x) * 0.75f + nx, y + (tipY - y) * 0.75f + ny, tipX, tipY)
        path.cubicTo(x + (tipX - x) * 0.75f - nx, y + (tipY - y) * 0.75f - ny, x + (tipX - x) * 0.25f - nx, y + (tipY - y) * 0.25f - ny, x, y)
        leafPaint.color = color
        canvas.drawPath(path, leafPaint)
    }

    private companion object {
        val STEM = Color.rgb(123, 196, 127)
        val LEAF = Color.rgb(129, 199, 132)
        val LEAF_BRIGHT = Color.rgb(197, 225, 165)
        val DRY = Color.rgb(181, 167, 122)
        val POT = Color.rgb(201, 122, 84)
        val POT_RIM = Color.rgb(176, 101, 67)
        val FACE = Color.rgb(58, 36, 28)
        val CHEEK = Color.argb(150, 240, 150, 140)
    }
}
