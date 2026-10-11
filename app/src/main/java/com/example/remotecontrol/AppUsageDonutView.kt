package com.example.remotecontrol

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

data class AppUsageSegment(val label: String, val minutes: Int, val color: Int, val packageName: String = "")

class AppUsageDonutView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val segments = mutableListOf<AppUsageSegment>()
    private var totalMinutes = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 28f
        strokeCap = Paint.Cap.BUTT
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#1A1A2E")
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#AAAAAA")
    }

    fun setSegments(data: List<AppUsageSegment>) {
        segments.clear()
        segments.addAll(data)
        totalMinutes = data.sumOf { it.minutes }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (segments.isEmpty()) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.72f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        var startAngle = -90f
        val gap = 2f

        for (seg in segments) {
            val sweep = (seg.minutes.toFloat() / totalMinutes) * 360f - gap
            paint.color = seg.color
            canvas.drawArc(rect, startAngle, sweep.coerceAtLeast(0f), false, paint)
            startAngle += sweep + gap
        }

        // Markazda matn
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        val timeStr = if (hours > 0) "${hours} soat ${mins} d." else "${mins} daqiqa"

        labelPaint.textSize = 11f * resources.displayMetrics.scaledDensity
        canvas.drawText("BUGUN", cx, cy - 14f, labelPaint)

        textPaint.textSize = 15f * resources.displayMetrics.scaledDensity
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(timeStr, cx, cy + 14f, textPaint)
    }
}
