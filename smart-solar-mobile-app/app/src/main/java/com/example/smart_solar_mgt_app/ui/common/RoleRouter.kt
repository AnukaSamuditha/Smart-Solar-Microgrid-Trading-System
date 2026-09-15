package com.example.smart_solar_mgt_app.ui.common

import android.app.Activity
import android.content.Intent
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.ui.gridoperator.OperatorMainActivity
import com.example.smart_solar_mgt_app.ui.prosumer.ProsumerMainActivity

/**
 * The single place that maps Role -> landing Activity. Both SplashActivity and the
 * post-login/-registration redirects call this instead of duplicating the mapping.
 */
object RoleRouter {
    fun routeTo(activity: Activity, role: Role) {
        val target = when (role) {
            Role.PROSUMER -> ProsumerMainActivity::class.java
            Role.GRID_OPERATOR -> OperatorMainActivity::class.java
        }
        val intent = Intent(activity, target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }
}
