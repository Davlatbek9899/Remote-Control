package com.example.remotecontrol

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class DotsLoadingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var rotation = 0f
    private var animator: ValueAnimator? = null

    init {
        startAnimation()
    }

    private fun startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotation = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val strokeWidth = minOf(width, height) * 0.12f
        val radius = minOf(width, height) / 2f - strokeWidth / 2f - 4f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweepAngle = 280f
        val segments = 120

        canvas.save()
        canvas.rotate(rotation, cx, cy)

        for (i in 0 until segments) {
            val fraction = i.toFloat() / segments
            val alpha = (fraction * 255).toInt().coerceIn(0, 255)
            val sw = strokeWidth * (0.15f + fraction * 0.85f)

            paint.alpha = alpha
            paint.strokeWidth = sw
            paint.color = Color.parseColor("#1B8EF8")

            val startAngle = -sweepAngle / 2f + (sweepAngle / segments) * i
            canvas.drawArc(rect, startAngle, sweepAngle / segments + 0.5f, false, paint)
        }

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
