package com.example.remotecontrol

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class FixedWidthTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Har doim "00:00" kengligini o'lchaymiz
        val original = text
        text = "00:00"
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val fixedWidth = measuredWidth
        text = original
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(fixedWidth, MeasureSpec.EXACTLY),
            heightMeasureSpec
        )
    }
}