package com.buddhanag.luminux.monetization

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase.PurchaseState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private class FakeBilling(var price: String? = "$2.99", var reachable: Boolean = true) : BillingGateway {
        val owned = mutableListOf<Owned>()
        val acknowledged = mutableListOf<String>()
        override val updates = MutableSharedFlow<Pair<Int, List<Owned>>>()
        override suspend fun connect() = reachable
        override suspend fun productPrice() = price
        override suspend fun ownedPurchases() = owned.toList()
        override suspend fun acknowledge(token: String) { acknowledged += token }
        override fun launch(activity: Activity) = BillingResponseCode.OK
    }

    private fun pro(state: Int = PurchaseState.PURCHASED, acknowledged: Boolean = false, verified: Boolean = true) =
        Owned(listOf(ProStore.PRODUCT_ID), state, acknowledged, "token", verified)

    private fun fresh(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher())) { tmp.newFile("s.preferences_pb") }

    private class Setup(val store: ProStore, val billing: FakeBilling, val prefs: DataStore<Preferences>, val syncs: () -> Int)

    private fun TestScope.setup(billing: FakeBilling = FakeBilling(), prefs: DataStore<Preferences> = fresh()): Setup {
        var syncs = 0
        val store = ProStore(prefs, billing, backgroundScope) { syncs++ }
        return Setup(store, billing, prefs) { syncs }
    }

    @Test fun loadsTheProductLocked() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.store.refresh()
        assertEquals("$2.99", s.store.price.value)
        assertFalse(s.store.isUnlocked.value)
    }

    @Test fun withoutPlayThereIsNoPriceAndNoPurchase() = runTest(UnconfinedTestDispatcher()) {
        val s = setup(FakeBilling(reachable = false))
        s.store.refresh()
        assertNull(s.store.price.value)
        assertFalse(s.store.isUnlocked.value)
    }

    @Test fun buyingUnlocksAndARefundLocksAgain() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.billing.owned += pro()
        s.store.refresh()
        assertTrue(s.store.isUnlocked.value)
        assertEquals(listOf("token"), s.billing.acknowledged)
        assertEquals(true, s.prefs.data.first()[ProStore.IS_PRO])
        assertEquals(1, s.syncs())

        // Acknowledged now; a second refresh doesn't acknowledge again or touch the widgets.
        s.billing.owned[0] = pro(acknowledged = true)
        s.store.refresh()
        assertEquals(1, s.billing.acknowledged.size)
        assertEquals(1, s.syncs())

        s.billing.owned.clear()
        s.store.refresh()
        assertFalse(s.store.isUnlocked.value)
        assertEquals(false, s.prefs.data.first()[ProStore.IS_PRO])
        assertEquals(2, s.syncs())
    }

    @Test fun theLastKnownStateSurvivesARelaunch() = runTest(UnconfinedTestDispatcher()) {
        val prefs = fresh()
        val first = setup(prefs = prefs)
        first.billing.owned += pro()
        first.store.refresh()
        val second = setup(FakeBilling(reachable = false), prefs)
        second.store.load()
        assertTrue(second.store.isUnlocked.value)
    }

    @Test fun aPurchaseUpdateUnlocksAndAcknowledges() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.billing.updates.emit(BillingResponseCode.OK to listOf(pro()))
        assertTrue(s.store.isUnlocked.value)
        assertEquals(listOf("token"), s.billing.acknowledged)
        assertNull(s.store.message.value)
    }

    @Test fun pendingDoesNotUnlock() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.billing.updates.emit(BillingResponseCode.OK to listOf(pro(state = PurchaseState.PENDING)))
        assertFalse(s.store.isUnlocked.value)
        assertEquals("The purchase is waiting for approval. Pro unlocks as soon as it's approved.", s.store.message.value)
        assertTrue(s.billing.acknowledged.isEmpty())
        // Pending purchases also don't count in a refresh.
        s.billing.owned += pro(state = PurchaseState.PENDING)
        s.store.refresh()
        assertFalse(s.store.isUnlocked.value)
        assertTrue(s.billing.acknowledged.isEmpty())
    }

    @Test fun anUnverifiedPurchaseDoesNotUnlock() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.billing.updates.emit(BillingResponseCode.OK to listOf(pro(verified = false)))
        assertFalse(s.store.isUnlocked.value)
        assertEquals("Google Play couldn't confirm the purchase. Try restoring it.", s.store.message.value)
        s.billing.owned += pro(verified = false)
        s.store.refresh()
        assertFalse(s.store.isUnlocked.value)
    }

    @Test fun cancelSaysNothingAndErrorsExplain() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.billing.updates.emit(BillingResponseCode.USER_CANCELED to emptyList())
        assertNull(s.store.message.value)
        s.billing.updates.emit(BillingResponseCode.NETWORK_ERROR to emptyList())
        assertEquals("The purchase didn't go through. Check your connection and try again.", s.store.message.value)
        assertFalse(s.store.isPurchasing.value)
    }

    @Test fun alreadyOwnedRestores() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.billing.owned += pro(acknowledged = true)
        s.billing.updates.emit(BillingResponseCode.ITEM_ALREADY_OWNED to emptyList())
        assertTrue(s.store.isUnlocked.value)
    }

    @Test fun restoreExplainsWhenNothingIsFound() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.store.restore()
        assertEquals("No Luminux Pro purchase was found for this Google Account.", s.store.message.value)
        s.billing.owned += pro()
        s.store.restore()
        assertTrue(s.store.isUnlocked.value)
        assertNull(s.store.message.value)
    }

    @Test fun otherProductsAreIgnored() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.billing.owned += Owned(listOf("something.else"), PurchaseState.PURCHASED, false, "other", true)
        s.store.refresh()
        assertFalse(s.store.isUnlocked.value)
        assertTrue(s.billing.acknowledged.isEmpty())
    }
}
