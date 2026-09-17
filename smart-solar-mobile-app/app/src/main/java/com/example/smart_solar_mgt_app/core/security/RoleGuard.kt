package com.example.smart_solar_mgt_app.core.security

import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.domain.model.Session

/**
 * Role enforcement helper. Called from Repository methods (not just UI) so a role bypass isn't
 * possible by calling a repository directly, skipping whatever screen normally gates it.
 */
object RoleGuard {

    fun check(session: Session?, required: Role): Boolean = session?.role == required

    fun enforce(session: Session?, required: Role) {
        if (session == null) throw UnauthorizedAccessException("Not logged in")
        if (session.role != required) {
            throw UnauthorizedAccessException("Requires $required, but current session is ${session.role}")
        }
    }
}