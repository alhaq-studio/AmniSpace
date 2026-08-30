package com.alhaq.amniquest.billing

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.alhaq.amniquest.backed.repositories.UserRepository
import com.alhaq.amniquest.core.licensing.LicenseValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FdroidBillingManager @Inject constructor(
    private val userRepository: UserRepository
) : BillingManager {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isSupporter = MutableStateFlow(userRepository.userInfo.customization_info.isSupporter)
    override val isSupporter: StateFlow<Boolean> = _isSupporter.asStateFlow()

    private val _supporterTier = MutableStateFlow(userRepository.userInfo.customization_info.supporterTier)
    override val supporterTier: StateFlow<String?> = _supporterTier.asStateFlow()

    private val _isReady = MutableStateFlow(true)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    override fun launchBillingFlow(
        activity: Activity,
        productId: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        // Universal / F-Droid flavor redirects to transparent sponsor & donation hub
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://alhaq.uk/AmniSpace#plans"))
            activity.startActivity(intent)
            onComplete(true, "Opened AmniSpace Supporter Hub.")
        } catch (_: Exception) {
            onComplete(false, "Could not open web browser.")
        }
    }

    override fun activateOfflineLicense(
        licenseKey: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val payload = LicenseValidator.verifyLicense(licenseKey)
        if (payload != null) {
            scope.launch {
                val customInfo = userRepository.userInfo.customization_info
                customInfo.isSupporter = true
                customInfo.supporterTier = payload.type
                customInfo.supporterExpiry = payload.expires
                customInfo.offlineLicenseKey = licenseKey
                userRepository.updateUser()
                _isSupporter.value = true
                _supporterTier.value = payload.type
            }
            onComplete(true, "Offline ECDSA License Activated (" + payload.type + ")")
        } else {
            onComplete(false, "Invalid or expired cryptographic license key.")
        }
    }

    override fun refreshPurchases() {
        val key = userRepository.userInfo.customization_info.offlineLicenseKey
        if (!key.isNullOrBlank()) {
            val payload = LicenseValidator.verifyLicense(key)
            if (payload != null) {
                _isSupporter.value = true
                _supporterTier.value = payload.type
            } else {
                _isSupporter.value = false
                _supporterTier.value = null
            }
        }
    }
}
