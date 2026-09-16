package com.example.smart_solar_mgt_app.ui.prosumer.profile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import com.example.smart_solar_mgt_app.ui.auth.LoginActivity
import com.google.android.material.button.MaterialButton

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val viewModel: ProfileViewModel by viewModels {
        viewModelFactory {
            initializer { ProfileViewModel(ServiceLocator.authRepository, ServiceLocator.securityManager) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progress = view.findViewById<ProgressBar>(R.id.progressProfile)
        val groupContent = view.findViewById<View>(R.id.groupProfileContent)
        val tvFullName = view.findViewById<TextView>(R.id.tvFullName)
        val tvAccountStatus = view.findViewById<TextView>(R.id.tvAccountStatus)
        val tvNic = view.findViewById<TextView>(R.id.tvNic)
        val tvEmail = view.findViewById<TextView>(R.id.tvEmail)
        val tvPhone = view.findViewById<TextView>(R.id.tvPhone)
        val tvAddress = view.findViewById<TextView>(R.id.tvAddress)
        val btnEditProfile = view.findViewById<MaterialButton>(R.id.btnEditProfile)
        val btnRequestDeactivation = view.findViewById<MaterialButton>(R.id.btnRequestDeactivation)
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnLogout)

        btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }

        btnRequestDeactivation.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Request Deactivation?")
                .setMessage("Your account will be marked for deactivation review. You can keep using the app until it's processed.")
                .setPositiveButton("Request Deactivation") { _, _ -> viewModel.requestDeactivation() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnLogout.setOnClickListener {
            viewModel.logout()
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            requireActivity().finish()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            progress.isVisible = state is ProfileUiState.Loading
            groupContent.isVisible = state is ProfileUiState.Loaded

            when (state) {
                is ProfileUiState.Loaded -> {
                    val user = state.user
                    tvFullName.text = user.name
                    tvAccountStatus.text = user.accountStatus.name.replace('_', ' ')
                    tvAccountStatus.setTextColor(colorFor(user.accountStatus))
                    tvNic.text = "NIC: ${user.nic}"
                    tvEmail.text = user.email
                    tvPhone.text = user.phone.orEmpty()
                    tvAddress.text = "Address: ${user.address.orEmpty()}"

                    when (user.accountStatus) {
                        AccountStatus.ACTIVE -> {
                            btnRequestDeactivation.isEnabled = true
                            btnRequestDeactivation.text = "Request Deactivation"
                            btnRequestDeactivation.isVisible = true
                        }
                        AccountStatus.DEACTIVATION_REQUESTED -> {
                            btnRequestDeactivation.isEnabled = false
                            btnRequestDeactivation.text = "Deactivation Requested - Pending Review"
                            btnRequestDeactivation.isVisible = true
                        }
                        else -> btnRequestDeactivation.isVisible = false
                    }
                }
                is ProfileUiState.Error -> Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                ProfileUiState.Loading -> Unit
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProfile()
    }

    private fun colorFor(status: AccountStatus): Int = when (status) {
        AccountStatus.ACTIVE -> Color.parseColor("#2E7D32")
        AccountStatus.PENDING_APPROVAL, AccountStatus.DEACTIVATION_REQUESTED -> Color.parseColor("#F9A825")
        AccountStatus.SUSPENDED, AccountStatus.REJECTED -> Color.parseColor("#C62828")
        AccountStatus.DEACTIVATED -> Color.parseColor("#757575")
    }
}
