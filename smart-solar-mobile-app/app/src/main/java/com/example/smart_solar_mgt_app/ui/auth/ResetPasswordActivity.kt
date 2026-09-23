package com.example.smart_solar_mgt_app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Sets a password for a Prosumer whose account is Invited - either just-approved (see
 * ProsumerMobileApprovalEmailTemplate) or staff-created and not yet finished the web flow.
 * Reachable two ways: automatically after a login attempt reports PasswordNotSet (see
 * LoginActivity.render, code field left blank), or via the deep link in the approval email
 * (solarsync://reset-password?token=... - see AndroidManifest.xml's intent-filter on this
 * Activity), which pre-fills and hides the code field since it's already known.
 */
class ResetPasswordActivity : AppCompatActivity() {

    private val viewModel: ResetPasswordViewModel by viewModels {
        viewModelFactory { initializer { ResetPasswordViewModel(ServiceLocator.remoteAuthRepository) } }
    }

    private lateinit var tilCode: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etCode: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnSubmit: MaterialButton
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        tilCode = findViewById(R.id.tilResetCode)
        tilPassword = findViewById(R.id.tilResetPassword)
        tilConfirmPassword = findViewById(R.id.tilResetConfirmPassword)
        etCode = findViewById(R.id.etResetCode)
        etPassword = findViewById(R.id.etResetPassword)
        etConfirmPassword = findViewById(R.id.etResetConfirmPassword)
        btnSubmit = findViewById(R.id.btnResetSubmit)
        progress = findViewById(R.id.progressReset)
        val tvBackToLogin = findViewById<TextView>(R.id.tvBackToLogin)

        btnSubmit.setOnClickListener {
            tilCode.error = null
            tilPassword.error = null
            tilConfirmPassword.error = null
            viewModel.onSubmitClicked(
                etCode.text?.toString().orEmpty(),
                etPassword.text?.toString().orEmpty(),
                etConfirmPassword.text?.toString().orEmpty()
            )
        }

        tvBackToLogin.setOnClickListener { finish() }

        applyDeepLinkToken()

        viewModel.state.observe(this) { render(it) }
    }

    // solarsync://reset-password?token=... - the code is already known, so hide that field and
    // jump straight to the password fields instead of asking the prosumer to copy it in by hand
    private fun applyDeepLinkToken() {
        val token = intent?.data?.getQueryParameter("token")?.takeIf { it.isNotBlank() } ?: return
        etCode.setText(token)
        tilCode.isVisible = false
        etPassword.requestFocus()
    }

    private fun render(state: ResetPasswordUiState) {
        progress.isVisible = state is ResetPasswordUiState.Loading
        btnSubmit.isEnabled = state !is ResetPasswordUiState.Loading

        when (state) {
            is ResetPasswordUiState.FieldError -> when (state.field) {
                ResetPasswordField.CODE -> tilCode.error = state.message
                ResetPasswordField.PASSWORD -> tilPassword.error = state.message
                ResetPasswordField.CONFIRM_PASSWORD -> tilConfirmPassword.error = state.message
            }
            is ResetPasswordUiState.FormError -> Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            ResetPasswordUiState.Success -> showSuccessDialog()
            ResetPasswordUiState.Idle, ResetPasswordUiState.Loading -> Unit
        }
    }

    private fun showSuccessDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Password Set")
            .setMessage("Your password has been set. You can now log in.")
            .setCancelable(false)
            .setPositiveButton("Log In") { _, _ ->
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }
            .show()
    }
}
