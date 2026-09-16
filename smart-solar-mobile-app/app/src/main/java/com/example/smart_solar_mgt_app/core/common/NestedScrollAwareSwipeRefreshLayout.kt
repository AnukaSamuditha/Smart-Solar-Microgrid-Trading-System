package com.example.smart_solar_mgt_app.core.common

import android.content.Context
import android.util.AttributeSet
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Fixes SwipeRefreshLayout wrapping a RecyclerView inside a BottomSheetBehavior sheet:
 * BottomSheetBehavior's nested-scroll handling asks whichever view re-dispatches the scroll to
 * it - that's this SwipeRefreshLayout, not the RecyclerView inside it - whether it
 * canScrollVertically(-1) to decide if a downward drag should scroll the list or collapse the
 * sheet. Plain SwipeRefreshLayout never overrides that check, so it always answers false, making
 * the sheet collapse on every backward scroll instead of only once the list is truly at its top.
 * Delegating to the actual scrolling child fixes that.
 */
class NestedScrollAwareSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    override fun canScrollVertically(direction: Int): Boolean {
        val child = if (childCount > 0) getChildAt(0) else null
        return child?.canScrollVertically(direction) ?: super.canScrollVertically(direction)
    }
}
