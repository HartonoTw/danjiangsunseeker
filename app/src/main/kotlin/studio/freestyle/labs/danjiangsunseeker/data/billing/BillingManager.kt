package studio.freestyle.labs.danjiangsunseeker.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import studio.freestyle.labs.danjiangsunseeker.data.settings.PremiumStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play 應用程式內購買（升級專業版）。一次性商品 [PRODUCT_ID]（INAPP）。
 *
 * 流程：連線 → 查商品 → [launchPurchase] 開啟付款 → [onPurchasesUpdated] 收到結果 →
 * 確認（acknowledge）後寫入 [PremiumStore.setPaid]。啟動時 [start] 會 `queryPurchases` 還原已購買狀態。
 *
 * 需在 Google Play Console 建立並啟用商品 ID「[PRODUCT_ID]」，且以測試軌道 / license tester 測試；
 * 本機未設定前 [productDetails] 會是 null（[launchPurchase] 回呼 onUnavailable）。
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val premiumStore: PremiumStore,
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    @Volatile private var productDetails: ProductDetails? = null

    /** 啟動連線並還原購買狀態；可重複呼叫（已就緒時只重查購買）。 */
    fun start() {
        if (billingClient.isReady) {
            queryProductDetails()
            queryPurchases()
        } else {
            runCatching { billingClient.startConnection(this) }
        }
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            queryProductDetails()
            queryPurchases()
        } else {
            Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
        }
    }

    override fun onBillingServiceDisconnected() {
        // 下次需要時再由 start() 重新連線即可。
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { result, products ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = products.firstOrNull()
            } else {
                Log.w(TAG, "queryProductDetails failed: ${result.debugMessage}")
            }
        }
    }

    /**
     * 開啟付款流程。若商品尚未就緒（未連線 / Play Console 未設定），觸發重新連線並回呼 [onUnavailable]。
     */
    fun launchPurchase(activity: Activity, onUnavailable: () -> Unit) {
        val details = productDetails
        if (details == null) {
            start()
            onUnavailable()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach(::handlePurchase)
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val ownsPro = purchases.any {
                it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            // 還原 / 對帳：已擁有則解鎖，否則維持未付費（不影響看廣告的暫時解鎖）。
            scope.launch { premiumStore.setPaid(ownsPro) }
            purchases.forEach(::handlePurchase)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        scope.launch { premiumStore.setPaid(true) }

        // 未確認的購買須在 3 天內 acknowledge，否則會自動退款。
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(ackParams) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "acknowledge failed: ${result.debugMessage}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "BillingManager"
        private const val PRODUCT_ID = "pro_unlock_lifetime"
    }
}
