package com.goodwy.contacts.extensions

import android.view.View

fun View.setHeightAndWidth(size: Int) {
    val lp = layoutParams
    lp.height = size
    lp.width = size
    layoutParams = lp
}

fun View.setWidth(size: Int) {
    val lp = layoutParams
    lp.width = size
    layoutParams = lp
}
