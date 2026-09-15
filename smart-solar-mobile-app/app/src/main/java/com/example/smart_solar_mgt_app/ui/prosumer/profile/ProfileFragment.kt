package com.example.smart_solar_mgt_app.ui.prosumer.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.ui.auth.LoginActivity
import com.google.android.material.button.MaterialButton

/** Real content (view/edit profile, deactivation request) lands in the Profile Management phase. */
class ProfileFragment : Fragment(R.layout.fragment_profile_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val session = ServiceLocator.securityManager.currentSession()
        view.findViewById<TextView>(R.id.tvProfilePlaceholder).text =
            "Profile - coming soon\nLogged in as: ${session?.userId}"

        view.findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            ServiceLocator.securityManager.logout()
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            requireActivity().finish()
        }
    }
}
