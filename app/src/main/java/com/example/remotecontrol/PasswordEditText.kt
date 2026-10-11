package com.example.remotecontrol

import android.content.Context
import android.graphics.Color
import android.text.*
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatEditText

class PasswordEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private var isUpdating = false
    private var rawDigits = ""
    private val maxLen = 4
    private val starColor = Color.parseColor("#FFD60A")
    private val digitColor = Color.WHITE

    init {
        inputType = InputType.TYPE_CLASS_NUMBER
        gravity = Gravity.CENTER
        isCursorVisible = false
        textSize = 26f
        letterSpacing = 0.4f
        setTextColor(digitColor)
        updateDisplay()

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                // Faqat raqamlarni ajratib olish
                val typed = s?.toString()?.filter { it.isDigit() }?.take(maxLen) ?: ""
                if (typed != rawDigits) {
                    rawDigits = typed
                    updateDisplay()
                }
            }
        })
    }

    private fun updateDisplay() {
        isUpdating = true
        val spannable = SpannableStringBuilder()
        for (i in 0 until maxLen) {
            if (i > 0) spannable.append("   ")
            val start = spannable.length
            if (i < rawDigits.length) {
                spannable.append(rawDigits[i].toString())
                spannable.setSpan(
                    ForegroundColorSpan(digitColor),
                    start, spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                spannable.append("*")
                spannable.setSpan(
                    ForegroundColorSpan(starColor),
                    start, spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    RelativeSizeSpan(1.4f),
                    start, spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        setText(spannable)
        try { setSelection(text?.length ?: 0) } catch (_: Exception) {}
        isUpdating = false
    }

    fun getPassword(): String = rawDigits
}
