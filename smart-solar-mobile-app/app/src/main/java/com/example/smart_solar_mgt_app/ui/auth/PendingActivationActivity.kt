package com.example.smart_solar_mgt_app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.core.common.SyncStatus
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.OutboxOperationType
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shown after a successful self-registration, and again on every login attempt while the
 * request is still PendingApproval (see LoginActivity.render). When reached right after
 * registering (EXTRA_NIC/EXTRA_EMAIL present), also names the email the approval notice will
 * arrive at, and shows whether the registration has reached the server yet - see
 * core/sync/SyncManager.kt. Deliberately says nothing about who reviews it (Backoffice vs.
 * Grid Operator) - that's an internal detail the prosumer doesn't need.
 */
class PendingActivationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_activation)

        findViewById<MaterialButton>(R.id.btnBackToLogin).setOnClickListener {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }

        intent.getStringExtra(EXTRA_EMAIL)?.let { email ->
            findViewById<TextView>(R.id.tvPendingMessage).text =
                "Your account request has been submitted. Once it's approved, you'll receive an email at $email with a link to set your password."
        }

        val nic = intent.getStringExtra(EXTRA_NIC)
        if (nic != null) {
            showSyncStatus(nic)
        }
    }

    private fun showSyncStatus(nic: String) {
        val tvSyncStatus = findViewById<TextView>(R.id.tvSyncStatus)
        lifecycleScope.launch {
            val outboxOperation = withContext(Dispatchers.IO) {
                ServiceLocator.localDbManager.getOutboxOperationForEntity(OutboxOperationType.PROSUMER_REGISTER, nic)
            }
            tvSyncStatus.isVisible = true
            tvSyncStatus.text = when {
                outboxOperation == null -> "Submitted to the server."
                outboxOperation.status == SyncStatus.SYNC_FAILED ->
                    "Couldn't submit your request: ${outboxOperation.lastError ?: "unknown error"}"
                else -> "Waiting for a connection to submit your request..."
            }
        }
    }

    companion object {
        const val EXTRA_NIC = "nic"
        const val EXTRA_EMAIL = "email"
    }
}
