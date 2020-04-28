package com.wading.bingokt

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton

class NumberButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AppCompatButton(context, attrs, defStyleAttr) {

    var number = 0
    var picked = false
    var pos = 0

}