package com.boimin.beatradar.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A tip size and Play's localised price for it. */
data class Coffee(val productId: String, val label: String, val price: String, internal val details: ProductDetails)

sealed interface CoffeeState {
    data object Loading : CoffeeState
    /** Billing isn't available (no Play Store, products not set up): the Settings row is hidden. */
    data object Unavailable : CoffeeState
    data class Ready(val coffees: List<Coffee>, val thanks: Boolean = false, val pending: Boolean = false) : CoffeeState
}

/**
 * "Buy me a coffee" (PLAN §8): consumable Play products, so a tip can be given again. Nothing is unlocked and no
 * server is involved; a purchase is consumed (which also acknowledges it) as soon as Play confirms payment.
 */
class CoffeeBilling(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<CoffeeState>(CoffeeState.Loading)
    val state: StateFlow<CoffeeState> = _state

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.forEach(::handle) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    _state.value = CoffeeState.Unavailable
                    return
                }
                scope.launch { load() }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private suspend fun load() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            PRODUCTS.keys.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
            },
        ).build()
        val result = client.queryProductDetails(params)
        val coffees = result.productDetailsList.orEmpty()
            .mapNotNull { d -> d.oneTimePurchaseOfferDetails?.let { Coffee(d.productId, PRODUCTS[d.productId] ?: d.name, it.formattedPrice, d) } }
            .sortedBy { PRODUCTS.keys.indexOf(it.productId) }
        _state.value = if (coffees.isEmpty()) CoffeeState.Unavailable else CoffeeState.Ready(coffees)
        // A tip paid while the app was closed (e.g. a pending cash payment) is consumed now.
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
            .purchasesList.forEach(::handle)
    }

    fun buy(activity: Activity, coffee: Coffee) {
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(coffee.details).build()
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build())
    }

    fun thanksShown() {
        val s = _state.value
        if (s is CoffeeState.Ready) _state.value = s.copy(thanks = false, pending = false)
    }

    private fun handle(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> scope.launch {
                client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
                (_state.value as? CoffeeState.Ready)?.let { _state.value = it.copy(thanks = true, pending = false) }
            }
            // E.g. a convenience-store or e-wallet payment that completes later.
            Purchase.PurchaseState.PENDING -> (_state.value as? CoffeeState.Ready)?.let { _state.value = it.copy(pending = true) }
            else -> Unit
        }
    }

    fun close() {
        client.endConnection()
    }

    companion object {
        /** Play Console product ids → labels. Prices come from Play, per country. */
        val PRODUCTS = linkedMapOf("coffee_small" to "Small coffee", "coffee_medium" to "Medium coffee", "coffee_large" to "Large coffee")
    }
}
