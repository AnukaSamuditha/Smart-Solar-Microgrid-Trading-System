package com.example.smart_solar_mgt_app.core.security

import com.example.smart_solar_mgt_app.domain.model.AccountStatus

/**
 * The single place that decides which account statuses may log in. Kept separate from
 * SecurityManagerImpl so this specific business decision - most notably that
 * DEACTIVATION_REQUESTED still allows login, since a request isn't a deactivation - is a named,
 * documented, unit-testable rule rather than an inline `!= ACTIVE` check.
 */
object AccountLoginPolicy {
    fun canLogin(status: AccountStatus): Boolean = when (status) {
        AccountStatus.ACTIVE -> true
        // Requested, not deactivated - the account stays usable until Backoffice actually
        // processes the request. Flip this if the intended business rule is the opposite.
        AccountStatus.DEACTIVATION_REQUESTED -> true
        AccountStatus.PENDING_APPROVAL -> false
        AccountStatus.DEACTIVATED -> false
        AccountStatus.SUSPENDED -> false
        AccountStatus.REJECTED -> false
    }
}
