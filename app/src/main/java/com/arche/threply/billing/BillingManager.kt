package com.arche.threply.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.android.billingclient.api.*
import com.arche.threply.data.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pricing option matching iOS PricingOption.
 */
data class PricingOption(
    val id: String,
    val productId: String,
    val name: String,
    val defaultPrice: String,
    val subtitle: String,
    val badgeText: String?,
    val badgeColor: Color
) {
    companion object {
        val defaultOptions = listOf(
            PricingOption(
                id = "com.arche.threply.pro.lifetime",
                productId = "com.arche.threply.pro.lifetime",
                name = "Lifetime",
                defaultPrice = "¥128.00",
                subtitle = "一次购买，永久解锁",
                badgeText = "Lifetime",
                badgeColor = Color(0xFFFFD700)
            ),
            PricingOption(
                id = "com.arche.threply.pro.annual",
                productId = "com.arche.threply.pro.annual",
                name = "Annually",
                defaultPrice = "¥78.00 /年",
                subtitle = "64% OFF 按年订阅",
                badgeText = "-64%",
                badgeColor = Color(0xFFFF8C00)
            ),
            PricingOption(
                id = "com.arche.threply.pro.monthly",
                productId = "com.arche.threply.pro.monthly",
                name = "Monthly",
                defaultPrice = "¥18.00 /月",
                subtitle = "灵活试用与短期需求",
                badgeText = null,
                badgeColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}

/**
 * Google Play Billing manager.
 * Equivalent to iOS StoreKitManager.
 */
class BillingManager private constructor(private val appContext: Context) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"

        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(context: Context): BillingManager =
            instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext).also { instance = it }
            }
    }

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val _isLoadingProducts = MutableStateFlow(false)
    val isLoadingProducts: StateFlow<Boolean> = _isLoadingProducts.asStateFlow()

    private val _isProcessingPurchase = MutableStateFlow(false)
    val isProcessingPurchase: StateFlow<Boolean> = _isProcessingPurchase.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private val _entitledProductIds = MutableStateFlow<Set<String>>(emptySet())
    val entitledProductIds: StateFlow<Set<String>> = _entitledProductIds.asStateFlow()

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connection established")
                    queryProducts()
                    queryPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    private fun queryProducts() {
        _isLoadingProducts.value = true

        // Subscriptions (annual, monthly)
        val subParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                PricingOption.defaultOptions
                    .filter { it.id != "com.arche.threply.pro.lifetime" }
                    .map {
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(it.productId)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    }
            )
            .build()

        // In-app purchase (lifetime)
        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("com.arche.threply.pro.lifetime")
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(subParams) { result, subDetails ->
            val details = _productDetails.value.toMutableMap()
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                subDetails.forEach { details[it.productId] = it }
            }

            billingClient.queryProductDetailsAsync(inAppParams) { result2, inAppDetails ->
                if (result2.responseCode == BillingClient.BillingResponseCode.OK) {
                    inAppDetails.forEach { details[it.productId] = it }
                }
                _productDetails.value = details
                _isLoadingProducts.value = false
            }
        }
    }

    private fun queryPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val entitled = mutableSetOf<String>()
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { purchase ->
                        entitled.addAll(purchase.products)
                    }

                // Also check in-app purchases
                billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                ) { result2, inAppPurchases ->
                    if (result2.responseCode == BillingClient.BillingResponseCode.OK) {
                        inAppPurchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                            .forEach { purchase ->
                                entitled.addAll(purchase.products)
                            }
                    }
                    _entitledProductIds.value = entitled
                    PrefsManager.setProEntitled(appContext, entitled.isNotEmpty())
                }
            }
        }
    }

    fun displayPrice(option: PricingOption): String {
        val details = _productDetails.value[option.productId] ?: return option.defaultPrice
        // For subscriptions
        details.subscriptionOfferDetails?.firstOrNull()?.let { offer ->
            return offer.pricingPhases.pricingPhaseList.firstOrNull()?.formattedPrice
                ?: option.defaultPrice
        }
        // For one-time purchase
        details.oneTimePurchaseOfferDetails?.let { offer ->
            return offer.formattedPrice
        }
        return option.defaultPrice
    }

    fun isEntitled(option: PricingOption): Boolean =
        _entitledProductIds.value.contains(option.productId)

    fun purchase(activity: Activity, option: PricingOption) {
        val details = _productDetails.value[option.productId]
        if (details == null) {
            _lastErrorMessage.value = "价格尚未加载完成，请稍后再试。"
            return
        }

        _isProcessingPurchase.value = true

        val flowParams = BillingFlowParams.newBuilder()

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)

        // For subscriptions, need to set offer token
        details.subscriptionOfferDetails?.firstOrNull()?.let { offer ->
            productDetailsParamsBuilder.setOfferToken(offer.offerToken)
        }

        flowParams.setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))

        billingClient.launchBillingFlow(activity, flowParams.build())
    }

    fun restorePurchases() {
        _isRestoring.value = true
        queryPurchases()
        _isRestoring.value = false
    }

    fun clearError() {
        _lastErrorMessage.value = null
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        _isProcessingPurchase.value = false

        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledgePurchase(purchase)
                    }
                }
                queryPurchases()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // User cancelled, no error message needed
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryPurchases()
            }
            else -> {
                _lastErrorMessage.value = "购买失败，请稍后再试。(${result.responseCode})"
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "Failed to acknowledge: ${result.debugMessage}")
            }
        }
    }
}
