package com.example.smart_solar_mgt_app.ui.prosumer

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.smart_solar_mgt_app.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class ProsumerMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Explicit edge-to-edge: the root no longer reserves any system-bar padding for
        // itself, so the NavHost (and every screen's background) fills the true screen bounds
        // by default. Each screen reclaims the clearance its own content needs via
        // View.applyEdgeToEdgeContentPadding; the floating nav pill below gets its own.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_prosumer_main)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostProsumer) as NavHostFragment
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavProsumer)
        bottomNav.setupWithNavController(navHostFragment.navController)

        val bottomNavBaseMargin = (bottomNav.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (v.layoutParams as ViewGroup.MarginLayoutParams).apply {
                bottomMargin = bottomNavBaseMargin + bars.bottom
            }.also { v.layoutParams = it }
            insets
        }
        ViewCompat.requestApplyInsets(bottomNav)
    }
}
