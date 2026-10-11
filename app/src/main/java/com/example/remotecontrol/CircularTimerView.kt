package com.example.remotecontrol

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class CircularTimerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0.33f // 0..1
    private var draggable = true
    private var onAngleChange: ((Float) -> Unit)? = null
    private var thumbAngle = 0f
    private var lastAngle = -1f  // oldingi burchakni eslab qolamiz

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#E8EEF4")
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.BUTT
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1B8EF8")
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.82f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        canvas.drawCircle(cx, cy, radius, trackPaint)

        val sweepAngle = progress * 360f
        val startAngle = -90f

        val gradientMatrix = Matrix()
        gradientMatrix.setRotate(startAngle, cx, cy)

        val gradient = SweepGradient(cx, cy,
            intArrayOf(
                Color.parseColor("#00A8D8"),
                Color.parseColor("#1B8EF8"),
                Color.parseColor("#0A5FCC")
            ),
            floatArrayOf(0f, 0.5f, 1f)
        )
        gradient.setLocalMatrix(gradientMatrix)
        progressPaint.shader = gradient
        canvas.drawArc(rect, startAngle, sweepAngle, false, progressPaint)

        thumbAngle = -90f + sweepAngle
        val thumbX = cx + radius * cos(Math.toRadians(thumbAngle.toDouble())).toFloat()
        val thumbY = cy + radius * sin(Math.toRadians(thumbAngle.toDouble())).toFloat()

        thumbPaint.color = Color.parseColor("#881B8EF8")
        canvas.drawCircle(thumbX, thumbY, 22f, thumbPaint)
        thumbPaint.color = Color.parseColor("#1B8EF8")
        canvas.drawCircle(thumbX, thumbY, 14f, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!draggable) return false
        val cx = width / 2f
        val cy = height / 2f
        val dx = event.x - cx
        val dy = event.y - cy

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Bosishda hozirgi burchakni eslab qolamiz
                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                if (angle < 0) angle += 360f
                if (angle > 360f) angle -= 360f
                lastAngle = angle
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                if (angle < 0) angle += 360f
                if (angle > 360f) angle -= 360f

                // Yuqori chegaradan (360->0) o'tishni bloklash
                // Agar oldingi burchak 270+ va yangi 90- bo'lsa — bu sakrash
                if (lastAngle > 270f && angle < 90f) {
                    // Maksimumga qo'yib qo'yamiz
                    angle = 360f
                }
                // Pastki chegaradan (0->360) o'tishni bloklash
                if (lastAngle < 90f && angle > 270f) {
                    // Minimumga qo'yib qo'yamiz
                    angle = 0f
                }

                // Minimum: 4 daraja (~1 daqiqa), Maksimum: 360 daraja (90 daqiqa)
                angle = angle.coerceIn(0f, 360f)

                lastAngle = angle
                progress = angle / 360f
                onAngleChange?.invoke(angle)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        invalidate()
    }

    fun setDraggable(d: Boolean) { draggable = d }

    fun setOnAngleChangeListener(listener: (Float) -> Unit) {
        onAngleChange = listener
    }
}