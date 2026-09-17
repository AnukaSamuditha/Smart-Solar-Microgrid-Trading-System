package com.example.smart_solar_mgt_app.ui.gridoperator

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.ui.auth.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Real Grid Operator dashboard: bottom-nav host for Home (pending approvals) / Scan QR /
 * Bookings (cross-prosumer overview) / Map. Supersedes the Phase 11 debug approval tool
 * (app/src/debug/) for approve/reject, though that tool is left in place for now.
 */
class OperatorMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Activity-level role guard: verify independently of how we were launched, not just
        // trusting that only RoleRouter ever starts this Activity.
        val session = ServiceLocator.securityManager.currentSession()
        if (session == null || session.role != Role.GRID_OPERATOR) {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
            return
        }

        // Explicit edge-to-edge: the root no longer reserves any system-bar padding for
        // itself, so the NavHost (and every screen's background) fills the true screen bounds
        // by default. Each screen reclaims the clearance its own content needs via
        // View.applyEdgeToEdgeContentPadding; the floating nav pill below gets its own.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_operator_main)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostOperator) as NavHostFragment
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavOperator)
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
