package com.buddhanag.luminux.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.buddhanag.luminux.BuildConfig
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.coroutines.resume

/** A purchase as ProStore needs it. `state` is a `Purchase.PurchaseState`. */
data class Owned(val productIds: List<String>, val state: Int, val acknowledged: Boolean, val token: String, val verified: Boolean)

/** Thin seam over BillingClient so ProStore is unit-testable. */
interface BillingGateway {
    suspend fun connect(): Boolean
    /** The Pro product's formatted price; null when Play can't be reached or has no such product. */
    suspend fun productPrice(): String?
    suspend fun ownedPurchases(): List<Owned>
    suspend fun acknowledge(token: String)
    /** A `BillingResponseCode`; the outcome arrives on [updates]. */
    fun launch(activity: Activity): Int
    val updates: Flow<Pair<Int, List<Owned>>>
}

class PlayBillingGateway(context: Context) : BillingGateway {
    override val updates = MutableSharedFlow<Pair<Int, List<Owned>>>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var product: ProductDetails? = null

    private val client = BillingClient.newBuilder(context)
        .setListener { result, purchases -> updates.tryEmit(result.responseCode to purchases.orEmpty().map(::owned)) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { c ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (c.isActive) c.resume(result.responseCode == BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    if (c.isActive) c.resume(false)
                }
            })
        }
    }

    override suspend fun productPrice(): String? {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(ProStore.PRODUCT_ID).setProductType(ProductType.INAPP).build()),
        ).build()
        val result = client.queryProductDetails(params)
        product = result.productDetailsList?.firstOrNull()
        return product?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
    }

    override suspend fun ownedPurchases(): List<Owned> {
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build())
        return if (result.billingResult.responseCode == BillingResponseCode.OK) result.purchasesList.map(::owned) else emptyList()
    }

    // Acknowledge, never consume: consuming makes Pro buyable again and drops it from queryPurchasesAsync.
    override suspend fun acknowledge(token: String) {
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build())
    }

    override fun launch(activity: Activity): Int {
        val details = product ?: return BillingResponseCode.ITEM_UNAVAILABLE
        val offerToken = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        offerToken?.let(product::setOfferToken)
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product.build())).build()
        return client.launchBillingFlow(activity, params).responseCode
    }

    private fun owned(p: Purchase) =
        Owned(p.products, p.purchaseState, p.isAcknowledged, p.purchaseToken, verifyPurchase(p.originalJson, p.signature))
}

/**
 * Checks Play's signature on a purchase with the app's licence key (Play Console → Monetization setup), the local
 * equivalent of StoreKit's verified transactions.
 * shortcut: without a key every purchase counts as verified; checkReleaseAdIds blocks a Play bundle until it's set.
 */
internal fun verifyPurchase(json: String, signature: String, key: String = BuildConfig.PLAY_LICENSE_KEY): Boolean {
    if (key.isEmpty()) return true
    return runCatching {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getMimeDecoder().decode(key)))
        Signature.getInstance("SHA1withRSA").run {
            initVerify(publicKey)
            update(json.toByteArray())
            verify(Base64.getMimeDecoder().decode(signature))
        }
    }.getOrDefault(false)
}
