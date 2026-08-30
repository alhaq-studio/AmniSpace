package com.alhaq.amniquest.core.licensing

import kotlinx.serialization.Serializable

@Serializable
data class LicensePayload(
    val email: String,
    val type: String,
    val expires: Long,
    val user_id: String? = null,
    val version: Int = 1
)
