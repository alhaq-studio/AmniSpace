package com.alhaq.amniquest.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface BillingManager {
    val isSupporter: StateFlow<Boolean>
    val supporterTier: StateFlow<String?>
    val isReady: StateFlow<Boolean>

    fun launchBillingFlow(
        activity: Activity,
        productId: String,
        onComplete: (success: Boolean, message: String?) -> Unit
    )

    fun activateOfflineLicense(
        licenseKey: String,
        onComplete: (success: Boolean, message: String?) -> Unit
    )

    fun refreshPurchases()
}
