package com.alhaq.amniquest.billing

import android.app.Activity
import android.content.Context
import com.alhaq.amniquest.backed.repositories.UserRepository
import com.alhaq.amniquest.core.licensing.LicenseValidator
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayBillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository
) : BillingManager, PurchasesUpdatedListener {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isSupporter = MutableStateFlow(userRepository.userInfo.customization_info.isSupporter)
    override val isSupporter: StateFlow<Boolean> = _isSupporter.asStateFlow()

    private val _supporterTier = MutableStateFlow(userRepository.userInfo.customization_info.supporterTier)
    override val supporterTier: StateFlow<String?> = _supporterTier.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var pendingPurchaseCallback: ((Boolean, String?) -> Unit)? = null
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isReady.value = true
                    queryProducts()
                    refreshPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                _isReady.value = false
            }
        })
    }

    private fun queryProducts() {
        val inAppProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("amniquest_lifetime_founder")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("amniquest_artisan_pack_1")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("amniquest_artisan_pack_2")
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val subProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("amniquest_monthly_supporter")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("amniquest_annual_supporter")
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val inAppParams = QueryProductDetailsParams.newBuilder().setProductList(inAppProducts).build()
        billingClient.queryProductDetailsAsync(inAppParams) { _, productDetailsList ->
            productDetailsList.forEach { productDetailsMap[it.productId] = it }
        }

        val subsParams = QueryProductDetailsParams.newBuilder().setProductList(subProducts).build()
        billingClient.queryProductDetailsAsync(subsParams) { _, productDetailsList ->
            productDetailsList.forEach { productDetailsMap[it.productId] = it }
        }
    }

    override fun launchBillingFlow(
        activity: Activity,
        productId: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val productDetails = productDetailsMap[productId]
        if (productDetails == null) {
            onComplete(false, "Product details not loaded. Please try again.")
            return
        }

        val productDetailsParamsList = if (productDetails.productType == BillingClient.ProductType.SUBS) {
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )
        } else {
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        pendingPurchaseCallback = onComplete
        val responseCode = billingClient.launchBillingFlow(activity, billingFlowParams).responseCode
        if (responseCode != BillingClient.BillingResponseCode.OK) {
            onComplete(false, "Failed to launch Google Play checkout.")
            pendingPurchaseCallback = null
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
            pendingPurchaseCallback?.invoke(true, "Purchase successful!")
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            pendingPurchaseCallback?.invoke(false, "Purchase cancelled.")
        } else {
            pendingPurchaseCallback?.invoke(false, billingResult.debugMessage.ifBlank { "Purchase failed." })
        }
        pendingPurchaseCallback = null
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { _ -> }
            }

            scope.launch {
                val customInfo = userRepository.userInfo.customization_info
                customInfo.isSupporter = true
                customInfo.supporterTier = purchase.products.firstOrNull() ?: "Patron"
                userRepository.updateUser()
                _isSupporter.value = true
                _supporterTier.value = customInfo.supporterTier
            }
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
            onComplete(true, "Offline License Validated (" + payload.type + ")")
        } else {
            onComplete(false, "Invalid or expired cryptographic license key.")
        }
    }

    override fun refreshPurchases() {
        if (!billingClient.isReady) return

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { _, purchases ->
            purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { handlePurchase(it) }
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { _, purchases ->
            purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { handlePurchase(it) }
        }
    }
}
