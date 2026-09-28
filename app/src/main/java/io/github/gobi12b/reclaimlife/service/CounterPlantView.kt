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
import kotlin.math.cos
import kotlin.math.sin

/**
 * The little plant beside the reel count in the floating counter. It sways gently, and as the
 * count nears the limit its leaves droop and fade a touch, like it needs water. Never a face.
 * A plain View, since the overlay window has no Compose lifecycle.
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

    /** [fraction] of the limit used; drooping starts past half and is full at the limit. */
    fun setProgress(fraction: Float) {
        droopTarget = ((fraction - 0.5f) / 0.5f).coerceIn(0f, 1f)
        if (!clock.isStarted) droop = droopTarget
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
        setMeasuredDimension((22 * density).toInt(), (26 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val baseX = w / 2
        val baseY = h - 2 * density
        val sway = 4f * sin(phase * 2 * Math.PI).toFloat() * (1f - 0.6f * droop) - 8f * droop
        val topX = baseX + sway * 0.35f * density
        val topY = baseY - h * (0.62f - 0.06f * droop)

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
    }
}
