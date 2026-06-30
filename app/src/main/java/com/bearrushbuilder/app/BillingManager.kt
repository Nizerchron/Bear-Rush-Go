package com.bearrushbuilder.app

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient
    
    // Status koneksi billing
    private val _isBillingConnected = MutableStateFlow(false)
    val isBillingConnected: StateFlow<Boolean> = _isBillingConnected.asStateFlow()

    // Data produk yang didapat dari console
    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails: StateFlow<List<ProductDetails>> = _productDetails.asStateFlow()

    // Callback invoked when a purchase is completed and verified
    var onPurchaseSuccess: ((productId: String) -> Unit)? = null

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        try {
            billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build()
                )
                .build()
            
            connectToGooglePlay()
        } catch (t: Throwable) {
            Log.e("BillingManager", "setupBillingClient failed: ${t.localizedMessage}", t)
        }
    }

    private fun connectToGooglePlay() {
        if (!::billingClient.isInitialized) {
            Log.e("BillingManager", "connectToGooglePlay cancelled: billingClient not initialized")
            return
        }
        Log.d("BillingManager", "Memulai koneksi ke Google Play Billing Client...")
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val responseCode = billingResult.responseCode
                val debugMessage = billingResult.debugMessage
                if (responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingManager", "Koneksi Billing Berhasil (Billing Connected)")
                    _isBillingConnected.value = true
                    // Query produk segera setelah konek
                    queryProducts()
                } else {
                    val desc = when (responseCode) {
                        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "BILLING_UNAVAILABLE (Layanan Billing tidak tersedia. Pastikan emulator/HP memiliki Google Play Store aktif, ter-update, dan sudah login akun Google)"
                        BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "DEVELOPER_ERROR (Masalah konfigurasi parameter/package name)"
                        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "FEATURE_NOT_SUPPORTED"
                        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "SERVICE_DISCONNECTED"
                        BillingClient.BillingResponseCode.USER_CANCELED -> "USER_CANCELED"
                        else -> "CODE_$responseCode"
                    }
                    Log.e("BillingManager", "Koneksi Billing GAGAL. Response Code: $responseCode ($desc). Debug Message: $debugMessage")
                    _isBillingConnected.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d("BillingManager", "Koneksi Billing Terputus (Billing Disconnected), trying to reconnect...")
                _isBillingConnected.value = false
            }
        })
    }

    private fun queryProducts() {
        if (!::billingClient.isInitialized) return
        Log.d("BillingManager", "Masuk ke fungsi queryProducts()...")
        try {
            val inAppProductIds = listOf("starter_pack", "popular_pack", "got_pack")
            val subProductIds = listOf("developer_mode")
            
            val combinedProductDetails = mutableListOf<ProductDetails>()
            var completedQueries = 0
            
            fun checkAndPublish() {
                completedQueries++
                if (completedQueries == 2) {
                    Log.d("BillingManager", "Query Selesai! Total produk terhubung: ${combinedProductDetails.size}")
                    combinedProductDetails.forEach {
                        Log.d("BillingManager", "-> Produk ditemukan: ${it.productId} | Tipe: ${it.productType} | Harga: ${it.oneTimePurchaseOfferDetails?.formattedPrice ?: it.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "N/A"}")
                    }
                    _productDetails.value = combinedProductDetails
                }
            }

            // 1. Query INAPP Products
            val inAppProductsList = inAppProductIds.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
            val inAppParams = QueryProductDetailsParams.newBuilder()
                .setProductList(inAppProductsList)
                .build()

            Log.d("BillingManager", "Memulai query produk INAPP...")
            billingClient.queryProductDetailsAsync(inAppParams) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                    synchronized(combinedProductDetails) {
                        combinedProductDetails.addAll(productDetailsList)
                    }
                } else {
                    Log.e("BillingManager", "Gagal query produk INAPP: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                }
                checkAndPublish()
            }

            // 2. Query SUBS Products
            val subProductsList = subProductIds.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
            val subParams = QueryProductDetailsParams.newBuilder()
                .setProductList(subProductsList)
                .build()

            Log.d("BillingManager", "Memulai query produk SUBS...")
            billingClient.queryProductDetailsAsync(subParams) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                    synchronized(combinedProductDetails) {
                        combinedProductDetails.addAll(productDetailsList)
                    }
                } else {
                    Log.e("BillingManager", "Gagal query produk SUBS: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                }
                checkAndPublish()
            }

        } catch (t: Throwable) {
            Log.e("BillingManager", "CRITICAL EXCEPTION di queryProducts: ${t.localizedMessage}", t)
        }
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        if (!::billingClient.isInitialized) {
            Log.e("BillingManager", "Cannot launch billing flow: billingClient not initialized")
            return
        }
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e("BillingManager", "Gagal meluncurkan billing flow: ${billingResult.debugMessage}")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d("BillingManager", "Pembelian dibatalkan pengguna.")
        } else {
            Log.e("BillingManager", "Pembelian gagal: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!::billingClient.isInitialized) return
        val products = purchase.products
        
        CoroutineScope(Dispatchers.IO).launch {
            if (products.contains("developer_mode")) {
                // developer_mode adalah fitur permanen (non-consumable), jadi hanya di-acknowledge
                if (!purchase.isAcknowledged) {
                    val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(acknowledgeParams) { billingResult ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d("BillingManager", "Developer mode berhasil diaktifkan secara permanen!")
                            // Trigger callback for developer_mode
                            products.forEach { prod ->
                                onPurchaseSuccess?.invoke(prod)
                            }
                        }
                    }
                }
            } else {
                // Paket koin bisa dibeli berkali-kali (consumable), jadi harus dikonsumsi
                val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                    
                billingClient.consumeAsync(consumeParams) { billingResult, _ ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d("BillingManager", "Purchase koin dikonsumsi, siap ditambahkan ke database!")
                        // Trigger callback for coin packages
                        products.forEach { prod ->
                            onPurchaseSuccess?.invoke(prod)
                        }
                    }
                }
            }
        }
    }
}
