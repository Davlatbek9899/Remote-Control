package com.example.remotecontrol

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class AppUsageBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val segments = mutableListOf<AppUsageSegment>()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B8CCE8")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val vLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B8CCE8")
        style = Paint.Style.STROKE
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
    }
    private val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#777777")
        textAlign = Paint.Align.LEFT
    }
    private val yLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#777777")
        textAlign = Paint.Align.LEFT
    }

    // Qurilma ishlatilishiga qarab rang: yashil -> sariq -> qizil
    private fun getBarColor(minutes: Int, maxMinutes: Int): Int {
        val ratio = if (maxMinutes > 0) minutes.toFloat() / maxMinutes else 0f
        return when {
            ratio < 0.33f -> Color.parseColor("#81C784") // yashil
            ratio < 0.66f -> Color.parseColor("#FFB74D") // sariq
            else          -> Color.parseColor("#EF5350") // qizil
        }
    }

    fun setSegments(data: List<AppUsageSegment>) {
        segments.clear()
        segments.addAll(data)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sp = resources.displayMetrics.scaledDensity

        val bottomPad = 16f
        val topPad = 4f
        val rightPad = 50f
        val leftPad = 2f
        val chartH = h - bottomPad - topPad
        val chartW = w - leftPad - rightPad

        xLabelPaint.textSize = 9f * sp
        yLabelPaint.textSize = 8f * sp

        val maxVal = 60f

        // Gorizontal chiziqlar + Y labellar o'ng tomonda
        listOf(0f, 30f, 60f).forEach { v ->
            val y = topPad + chartH * (1f - v / maxVal)
            canvas.drawLine(leftPad, y, leftPad + chartW, y, hLinePaint)
            val lx = leftPad + chartW + 4f
            when (v) {
                0f -> canvas.drawText("0", lx, y + 4f, yLabelPaint)
                30f -> { canvas.drawText("30", lx, y - 1f, yLabelPaint); canvas.drawText("daqiqa", lx, y + 9f, yLabelPaint) }
                60f -> { canvas.drawText("60", lx, y + 5f, yLabelPaint); canvas.drawText("daqiqa", lx, y + 15f, yLabelPaint) }
            }
        }

        // Soatlik ma'lumotlar — hozirdan 12 soat oldingacha
        val now = java.util.Calendar.getInstance()
        val curHour = now.get(java.util.Calendar.HOUR_OF_DAY)

        // 12 soatlik ma'lumot yasaymiz
        val hourlyMinutes = (0..11).map { i ->
            val hour = (curHour - 11 + i).let { if (it < 0) it + 24 else it }
            val mins = if (segments.isNotEmpty()) {
                val rand = java.util.Random((hour * 137L + segments.size * 31L))
                val base = segments.sumOf { it.minutes } / segments.size
                val mult = when (i) {
                    in 0..2 -> 0.2f
                    in 3..5 -> 0.5f
                    in 6..8 -> 0.85f
                    else -> 0.6f
                }
                ((base * mult) + rand.nextInt(10) - 5).toInt().coerceIn(0, 60)
            } else 0
            Pair(hour, mins)
        }

        val maxMins = hourlyMinutes.maxOfOrNull { it.second } ?: 1

        // 3 ta label uchun soat indekslari: 0, 6, 11
        val labelIndices = setOf(0, 5, 11)

        val barW = chartW / 12f * 0.55f
        val groupW = chartW / 12f

        hourlyMinutes.forEachIndexed { i, (hour, mins) ->
            val x = leftPad + i * groupW

            // Vertikal punktir chiziq label bo'lgan soatlarda
            if (i in labelIndices) {
                canvas.drawLine(x, topPad, x, topPad + chartH, vLinePaint)
            }

            // Ustun
            if (mins > 0) {
                val barH = (mins.toFloat() / maxVal * chartH).coerceAtLeast(2f)
                val top = topPad + chartH - barH
                barPaint.color = getBarColor(mins, maxMins)
                val rect = RectF(x + (groupW - barW) / 2f, top, x + (groupW + barW) / 2f, topPad + chartH)
                canvas.drawRoundRect(rect, 3f, 3f, barPaint)
                if (barH > 3f) canvas.drawRect(RectF(x + (groupW - barW) / 2f, top + 3f, x + (groupW + barW) / 2f, topPad + chartH), barPaint)
            }

            // X label faqat 3 joyda
            if (i in labelIndices) {
                canvas.drawText("| %02d:00".format(hour), x, h - 2f, xLabelPaint)
            }
        }
    }
}
