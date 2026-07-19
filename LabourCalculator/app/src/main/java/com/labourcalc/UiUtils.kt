package com.labourcalc

import android.view.View
import android.view.ViewGroup
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

/** Adds navigation-bar height to the view's bottom margin so FABs
 *  never sit under the back/home buttons. */
fun View.liftAboveNavBar() {
    val base = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { p ->
            p.bottomMargin = base + nb
            v.layoutParams = p
        }
        insets
    }
}
