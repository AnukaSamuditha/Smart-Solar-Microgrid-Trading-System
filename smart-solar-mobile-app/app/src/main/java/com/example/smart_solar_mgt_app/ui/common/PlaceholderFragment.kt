package com.example.smart_solar_mgt_app.ui.common

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.smart_solar_mgt_app.R

/**
 * Base for a tab whose real content lands in a later phase - shows a single centered
 * message so the tab is navigable and testable before its full feature is built.
 */
abstract class PlaceholderFragment(private val message: String) : Fragment(R.layout.fragment_placeholder) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tvPlaceholderMessage).text = message
    }
}
