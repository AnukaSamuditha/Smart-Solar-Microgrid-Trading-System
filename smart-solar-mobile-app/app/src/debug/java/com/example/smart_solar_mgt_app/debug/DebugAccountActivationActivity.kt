package com.example.smart_solar_mgt_app.debug

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import com.example.smart_solar_mgt_app.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DEBUG ONLY - not part of any release build (see app/src/debug/). Stands in for the real
 * Backoffice/API while it doesn't exist yet: flips a Prosumer's account from PENDING_APPROVAL to
 * ACTIVE. Calls the exact same AuthRepository.updateAccountStatus/getUsersPendingActivation
 * methods a real Backoffice-facing feature would use - this is a placeholder UI, not a bypass of
 * the repository layer. No login is required to use this tool, since account activation has no
 * role guard today (there is no Backoffice role in this app yet to guard against).
 *
 * Supersedes the earlier DebugBookingApprovalActivity, which is now redundant - the real Grid
 * Operator Home screen (ui/gridoperator/home) covers booking approval/rejection.
 */
class DebugAccountActivationActivity : AppCompatActivity() {

    private lateinit var adapter: DebugPendingUserAdapter
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_account_activation)

        tvEmpty = findViewById(R.id.tvDebugEmpty)
        val recyclerView = findViewById<RecyclerView>(R.id.rvDebugPendingUsers)
        adapter = DebugPendingUserAdapter(onActivate = { user -> activate(user) })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadPending()
    }

    private fun loadPending() {
        lifecycleScope.launch {
            val pending = withContext(Dispatchers.IO) { ServiceLocator.authRepository.getUsersPendingActivation() }
            adapter.submitList(pending)
            tvEmpty.isVisible = pending.isEmpty()
        }
    }

    private fun activate(user: User) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { ServiceLocator.authRepository.updateAccountStatus(user.nic, AccountStatus.ACTIVE) }
            Toast.makeText(this@DebugAccountActivationActivity, "Activated ${user.nic}", Toast.LENGTH_SHORT).show()
            loadPending()
        }
    }
}
