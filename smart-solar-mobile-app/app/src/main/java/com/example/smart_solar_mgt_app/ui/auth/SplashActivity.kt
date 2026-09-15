package com.example.smart_solar_mgt_app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.ui.common.RoleRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pure routing gate - no fields, no interaction. Reads the current session and sends the
 * user straight to Login or their role's dashboard, never back to this screen.
 */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { ServiceLocator.securityManager.currentSession() }
            if (session == null) {
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                finish()
            } else {
                RoleRouter.routeTo(this@SplashActivity, session.role)
            }
        }
    }
}
