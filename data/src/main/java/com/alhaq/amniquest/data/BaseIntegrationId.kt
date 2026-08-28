package com.alhaq.amniquest.data

import kotlinx.serialization.Serializable

/**
IMPORTANT: MAKE SURE TO CHANGE VALUES at [com.alhaq.amniquest.data.IntegrationId] AS WEll
 */
@Serializable
enum class BaseIntegrationId(
) {
    DEEP_FOCUS,
    HEALTH_CONNECT,
    SWIFT_MARK,
    AI_SNAP,
    EXTERNAL_INTEGRATION
}