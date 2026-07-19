package com.labourcalc

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Adds status-bar height to the view's top padding (edge-to-edge safe). */
fun View.padBelowStatusBar() {
    val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        v.setPadding(l, t + sb, r, b)
        insets
    }
}
