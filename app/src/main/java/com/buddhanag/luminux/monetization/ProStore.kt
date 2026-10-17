package com.buddhanag.luminux.monetization

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase.PurchaseState
import com.buddhanag.luminux.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The one-time "Luminux Pro" purchase (ProStore.swift): custom accent colours, wide live tiles, unlimited hub pins,
 * the slideshow, editing, subject lift, grid sizes and the hub background source. One INAPP product, acknowledged and
 * never consumed.
 */
class ProStore(
    private val prefs: DataStore<Preferences>,
    private val billing: BillingGateway,
    scope: CoroutineScope,
    private val widgetSync: suspend () -> Unit,
) {
    private val _isUnlocked = MutableStateFlow(false)
    /** Really bought. Hides the ads, lifts the pin limit and opens the viewer's Pro actions. */
    val isUnlocked: StateFlow<Boolean> = _isUnlocked
    /** Null while Play can't be reached or has no product; the offer card then says so. */
    val price = MutableStateFlow<String?>(null)
    val isPurchasing = MutableStateFlow(false)
    /** Set when a purchase or restore needs explaining; the Pro page shows it. */
    val message = MutableStateFlow<String?>(null)

    /**
     * Test installs are built with `-Pluminux.proFreeForTesting=true` so Pro features can be tried before the product
     * exists in Play Console. Play uploads never set it (checkReleaseAdIds).
     */
    val isTestBuild = BuildConfig.PRO_FREE_FOR_TESTING
    /** The accent, wide tiles, grid sizes and background source: free on test installs, unlike the ads and pins. */
    val unlocksPro get() = _isUnlocked.value || isTestBuild

    init {
        // Pending approvals that complete and purchases made while the page is open arrive here.
        scope.launch { billing.updates.collect { (code, list) -> onUpdate(code, list) } }
    }

    /** The last known state, so Pro features don't flicker off while Play answers. */
    suspend fun load() {
        _isUnlocked.value = prefs.data.first()[IS_PRO] == true
    }

    /** Loads the price and re-reads the purchases. A refunded purchase drops out of the query, which locks Pro again. */
    suspend fun refresh() {
        if (!billing.connect()) return
        if (price.value == null) price.value = billing.productPrice()
        val owned = billing.ownedPurchases().filter { PRODUCT_ID in it.productIds && it.verified && it.state == PurchaseState.PURCHASED }
        // Play refunds a purchase that isn't acknowledged within 3 days.
        owned.filterNot { it.acknowledged }.forEach { billing.acknowledge(it.token) }
        setUnlocked(owned.isNotEmpty())
    }

    fun purchase(activity: Activity) {
        if (price.value == null || isPurchasing.value) return
        message.value = null
        isPurchasing.value = true
        if (billing.launch(activity) != BillingResponseCode.OK) {
            isPurchasing.value = false
            message.value = FAILED
        }
    }

    suspend fun restore() {
        message.value = null
        refresh()
        if (!_isUnlocked.value) message.value = "No Luminux Pro purchase was found for this Google Account."
    }

    private suspend fun onUpdate(code: Int, purchases: List<Owned>) {
        isPurchasing.value = false
        when (code) {
            BillingResponseCode.OK -> purchases.filter { PRODUCT_ID in it.productIds }.forEach { p ->
                when {
                    !p.verified -> message.value = "Google Play couldn't confirm the purchase. Try restoring it."
                    p.state == PurchaseState.PENDING ->
                        message.value = "The purchase is waiting for approval. Pro unlocks as soon as it's approved."
                    p.state == PurchaseState.PURCHASED -> {
                        setUnlocked(true)
                        if (!p.acknowledged) billing.acknowledge(p.token)
                    }
                }
            }
            BillingResponseCode.USER_CANCELED -> Unit
            BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> message.value = FAILED
        }
    }

    private suspend fun setUnlocked(unlocked: Boolean) {
        _isUnlocked.value = unlocked
        if ((prefs.data.first()[IS_PRO] == true) == unlocked) return
        prefs.edit { it[IS_PRO] = unlocked }
        widgetSync()
    }

    companion object {
        const val PRODUCT_ID = "com.buddhanag.luminux.pro"
        val IS_PRO = booleanPreferencesKey("isPro")
        private const val FAILED = "The purchase didn't go through. Check your connection and try again."
    }
}
