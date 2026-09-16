package com.example.smart_solar_mgt_app.core.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Both the Prosumer and Grid Operator shells host their fragments in a NavHost that now bleeds
 * fully edge-to-edge (see ProsumerMainActivity/OperatorMainActivity), with the floating bottom
 * nav pill overlaid on top. A screen's own root view therefore needs to reclaim the status-bar
 * and floating-nav clearance its content actually needs - this is that one-line opt-in.
 *
 * Map screens (which want their map content to visibly reach the true top/bottom edges) simply
 * don't call this; every other screen does, in onViewCreated.
 */
fun View.applyEdgeToEdgeContentPadding(extraBottomForFloatingNav: Int = 0) {
    val initialPaddingTop = paddingTop
    val initialPaddingBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(
            top = initialPaddingTop + bars.top,
            bottom = initialPaddingBottom + bars.bottom + extraBottomForFloatingNav
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
