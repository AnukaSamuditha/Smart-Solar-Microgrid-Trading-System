package com.example.smart_solar_mgt_app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.ui.common.RoleRouter
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels {
        viewModelFactory { initializer { LoginViewModel(ServiceLocator.securityManager) } }
    }

    private lateinit var tilNic: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etNic: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var progressLogin: android.widget.ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tilNic = findViewById(R.id.tilNic)
        tilPassword = findViewById(R.id.tilPassword)
        etNic = findViewById(R.id.etNic)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressLogin = findViewById(R.id.progressLogin)
        val tvGoToRegister = findViewById<TextView>(R.id.tvGoToRegister)

        btnLogin.setOnClickListener {
            tilNic.error = null
            tilPassword.error = null
            viewModel.onLoginClicked(etNic.text?.toString().orEmpty(), etPassword.text?.toString().orEmpty())
        }

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        viewModel.state.observe(this) { render(it) }
    }

    private fun render(state: LoginUiState) {
        progressLogin.isVisible = state is LoginUiState.Loading
        btnLogin.isEnabled = state !is LoginUiState.Loading

        when (state) {
            is LoginUiState.FieldError -> when (state.field) {
                LoginField.NIC -> tilNic.error = state.message
                LoginField.PASSWORD -> tilPassword.error = state.message
            }
            is LoginUiState.FormError -> Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            is LoginUiState.Success -> RoleRouter.routeTo(this, state.role)
            LoginUiState.PendingApproval -> startActivity(Intent(this, PendingActivationActivity::class.java))
            LoginUiState.AccountCreationDenied -> startActivity(Intent(this, AccountDeniedActivity::class.java))
            LoginUiState.PasswordNotSet -> startActivity(Intent(this, ResetPasswordActivity::class.java))
            LoginUiState.Idle, LoginUiState.Loading -> Unit
        }
    }
}
