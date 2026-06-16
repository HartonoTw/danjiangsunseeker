package studio.freestyle.labs.danjiangsunseeker.presentation.common

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import studio.freestyle.labs.danjiangsunseeker.data.billing.BillingManager
import studio.freestyle.labs.danjiangsunseeker.data.settings.PremiumStore
import studio.freestyle.labs.danjiangsunseeker.domain.premium.PremiumGate
import studio.freestyle.labs.danjiangsunseeker.domain.premium.PremiumPage
import javax.inject.Inject

/**
 * 解鎖對話框（[PremiumUnlockDialog]）共用的控制器，並提供「是否已付費」給底部橫幅廣告判斷。
 *
 * 「升級專業版」走 Google Play Billing（[BillingManager]）；付費成功後由 BillingManager 寫入
 * [PremiumStore.setPaid]，[isPaid] 隨之更新。啟動時即連線並還原既有購買。
 */
@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val store: PremiumStore,
    private val billingManager: BillingManager,
    gate: PremiumGate,
) : ViewModel() {

    init {
        // 連線 Play Billing 並還原已購買狀態（重啟 App / 換裝置後維持專業版）。
        billingManager.start()
    }

    /** 是否永久專業版 — 底部橫幅廣告只在「已付費」時移除。 */
    val isPaid: StateFlow<Boolean> = gate.isPaid
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 升級專業版（永久解鎖 + 移除廣告）：開啟 Google Play 付款流程。
     * 商品尚未就緒（未連線 / Play Console 未設定）時回呼 [onUnavailable]。
     */
    fun upgradeToPro(activity: Activity, onUnavailable: () -> Unit) {
        billingManager.launchPurchase(activity, onUnavailable)
    }

    /**
     * 看廣告免費解鎖**指定頁**：自現在起 [AD_UNLOCK_DURATION_MS] 內該頁功能可用（廣告仍顯示）。
     * 由 [PremiumUnlockDialogHost] 在獎勵廣告播畢、使用者真正獲得獎勵後呼叫。
     */
    fun unlockViaAd(page: PremiumPage) {
        viewModelScope.launch {
            store.grantTempUnlock(page, System.currentTimeMillis() + AD_UNLOCK_DURATION_MS)
        }
    }

    companion object {
        /** 看廣告免費解鎖時長：2 小時。 */
        const val AD_UNLOCK_DURATION_MS = 2L * 60L * 60L * 1000L
    }
}
