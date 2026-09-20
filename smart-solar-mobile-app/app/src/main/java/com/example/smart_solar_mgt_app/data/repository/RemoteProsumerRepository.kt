package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.network.RemoteProsumerListOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerProfileOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerRegisterOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerReviewOutcome

/** Prosumer profile calls against the real Web Service (smart-solar-mgt-api). */
interface RemoteProsumerRepository {
    /** Public self-registration - POST /api/v1/prosumers/register. */
    fun register(nic: String, email: String, fullName: String, phone: String?, address: String?): RemoteProsumerRegisterOutcome

    /** Backoffice/Grid Operator only - GET /api/v1/prosumers?status=PendingApproval. */
    fun listPendingApproval(): RemoteProsumerListOutcome

    /** Backoffice/Grid Operator only - PATCH /api/v1/prosumers/{nic}/approve. */
    fun approve(nic: String): RemoteProsumerReviewOutcome

    /** Backoffice/Grid Operator only - PATCH /api/v1/prosumers/{nic}/deny. */
    fun deny(nic: String, reason: String?): RemoteProsumerReviewOutcome

    /** The logged-in prosumer's own profile - GET /api/v1/prosumers/me. */
    fun getMyProfile(): RemoteProsumerProfileOutcome
}
