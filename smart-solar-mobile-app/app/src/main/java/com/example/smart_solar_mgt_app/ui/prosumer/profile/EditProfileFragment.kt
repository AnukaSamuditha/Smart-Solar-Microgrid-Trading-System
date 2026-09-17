package com.example.smart_solar_mgt_app.ui.prosumer.profile

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.core.common.applyEdgeToEdgeContentPadding
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private val viewModel: EditProfileViewModel by viewModels {
        viewModelFactory {
            initializer { EditProfileViewModel(ServiceLocator.authRepository, ServiceLocator.securityManager) }
        }
    }

    private lateinit var tilFullName: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPhone: TextInputLayout
    private lateinit var tilAddress: TextInputLayout

    private lateinit var etNic: TextInputEditText
    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etAddress: TextInputEditText

    private lateinit var btnSaveProfile: MaterialButton
    private lateinit var progressEditProfile: ProgressBar

    private val fieldLayouts by lazy {
        mapOf(
            ProfileField.FULL_NAME to tilFullName,
            ProfileField.EMAIL to tilEmail,
            ProfileField.PHONE to tilPhone,
            ProfileField.ADDRESS to tilAddress
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.applyEdgeToEdgeContentPadding()
        view.findViewById<View>(R.id.btnBack).setOnClickListener { findNavController().popBackStack() }

        tilFullName = view.findViewById(R.id.tilFullName)
        tilEmail = view.findViewById(R.id.tilEmail)
        tilPhone = view.findViewById(R.id.tilPhone)
        tilAddress = view.findViewById(R.id.tilAddress)

        etNic = view.findViewById(R.id.etNic)
        etFullName = view.findViewById(R.id.etFullName)
        etEmail = view.findViewById(R.id.etEmail)
        etPhone = view.findViewById(R.id.etPhone)
        etAddress = view.findViewById(R.id.etAddress)

        btnSaveProfile = view.findViewById(R.id.btnSaveProfile)
        progressEditProfile = view.findViewById(R.id.progressEditProfile)
        val btnCancelEdit = view.findViewById<MaterialButton>(R.id.btnCancelEdit)

        btnCancelEdit.setOnClickListener { findNavController().popBackStack() }

        btnSaveProfile.setOnClickListener {
            fieldLayouts.values.forEach { it.error = null }
            viewModel.onSaveClicked(
                EditProfileValidator.Input(
                    fullName = etFullName.text?.toString().orEmpty(),
                    email = etEmail.text?.toString().orEmpty(),
                    phone = etPhone.text?.toString().orEmpty(),
                    address = etAddress.text?.toString().orEmpty()
                )
            )
        }

        viewModel.state.observe(viewLifecycleOwner) { state -> render(state) }
        viewModel.loadCurrentUser()
    }

    private fun render(state: EditProfileUiState) {
        progressEditProfile.isVisible = state is EditProfileUiState.Saving || state is EditProfileUiState.Loading
        btnSaveProfile.isEnabled = state !is EditProfileUiState.Saving && state !is EditProfileUiState.Loading

        when (state) {
            is EditProfileUiState.Prefill -> {
                etNic.setText(state.user.nic)
                etFullName.setText(state.user.name)
                etEmail.setText(state.user.email)
                etPhone.setText(state.user.phone.orEmpty())
                etAddress.setText(state.user.address.orEmpty())
            }
            is EditProfileUiState.FieldErrors -> state.errors.forEach { (field, message) ->
                fieldLayouts[field]?.error = message
            }
            is EditProfileUiState.FormError -> Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
            EditProfileUiState.Success -> {
                Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            EditProfileUiState.Loading, EditProfileUiState.Saving -> Unit
        }
    }
}
