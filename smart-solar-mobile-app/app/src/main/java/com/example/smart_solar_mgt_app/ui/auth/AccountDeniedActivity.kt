package com.example.smart_solar_mgt_app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_solar_mgt_app.R
import com.google.android.material.button.MaterialButton

/** Shown when a Prosumer whose self-registration was denied (Rejected, terminal) tries to log in. */
class AccountDeniedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_denied)

        findViewById<MaterialButton>(R.id.btnBackToLogin).setOnClickListener {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }
    }
}
