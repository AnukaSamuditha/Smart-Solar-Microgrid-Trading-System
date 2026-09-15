package com.example.smart_solar_mgt_app.ui.gridoperator

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.ui.auth.LoginActivity
import com.google.android.material.button.MaterialButton

/**
 * Placeholder landing screen for the GRID_OPERATOR role. Replaced by the real bottom-nav
 * dashboard (Home/Scan QR/Bookings/Map) in a later phase.
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

        setContentView(R.layout.activity_role_placeholder)
        findViewById<TextView>(R.id.tvPlaceholderTitle).text =
            "Grid Operator Dashboard (placeholder)\nLogged in as: ${session.userId}"

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            ServiceLocator.securityManager.logout()
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }
    }
}
