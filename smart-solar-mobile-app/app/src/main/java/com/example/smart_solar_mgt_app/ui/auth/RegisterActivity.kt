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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class RegisterActivity : AppCompatActivity() {

    private val viewModel: RegisterViewModel by viewModels {
        viewModelFactory { initializer { RegisterViewModel(ServiceLocator.localDbManager, ServiceLocator.syncManager) } }
    }

    private lateinit var tilNic: TextInputLayout
    private lateinit var tilFullName: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPhone: TextInputLayout
    private lateinit var tilAddress: TextInputLayout

    private lateinit var etNic: TextInputEditText
    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etAddress: TextInputEditText

    private lateinit var btnRegister: MaterialButton
    private lateinit var progressRegister: ProgressBar

    private val fieldLayouts by lazy {
        mapOf(
            RegisterField.NIC to tilNic,
            RegisterField.FULL_NAME to tilFullName,
            RegisterField.EMAIL to tilEmail,
            RegisterField.PHONE to tilPhone,
            RegisterField.ADDRESS to tilAddress
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        tilNic = findViewById(R.id.tilNic)
        tilFullName = findViewById(R.id.tilFullName)
        tilEmail = findViewById(R.id.tilEmail)
        tilPhone = findViewById(R.id.tilPhone)
        tilAddress = findViewById(R.id.tilAddress)

        etNic = findViewById(R.id.etNic)
        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etAddress = findViewById(R.id.etAddress)

        btnRegister = findViewById(R.id.btnRegister)
        progressRegister = findViewById(R.id.progressRegister)
        val tvGoToLogin = findViewById<TextView>(R.id.tvGoToLogin)

        btnRegister.setOnClickListener {
            fieldLayouts.values.forEach { it.error = null }
            viewModel.onRegisterClicked(
                RegistrationValidator.Input(
                    nic = etNic.text?.toString().orEmpty(),
                    fullName = etFullName.text?.toString().orEmpty(),
                    email = etEmail.text?.toString().orEmpty(),
                    phone = etPhone.text?.toString().orEmpty(),
                    address = etAddress.text?.toString().orEmpty()
                )
            )
        }

        tvGoToLogin.setOnClickListener { finish() }

        viewModel.state.observe(this) { render(it) }
    }

    private fun render(state: RegisterUiState) {
        progressRegister.isVisible = state is RegisterUiState.Loading
        btnRegister.isEnabled = state !is RegisterUiState.Loading

        when (state) {
            is RegisterUiState.FieldErrors -> state.errors.forEach { (field, message) ->
                fieldLayouts[field]?.error = message
            }
            is RegisterUiState.FormError -> Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            is RegisterUiState.PendingActivation -> {
                startActivity(
                    Intent(this, PendingActivationActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(PendingActivationActivity.EXTRA_NIC, state.nic)
                        putExtra(PendingActivationActivity.EXTRA_EMAIL, state.email)
                    }
                )
                finish()
            }
            RegisterUiState.Idle, RegisterUiState.Loading -> Unit
        }
    }
}
