package studio.freestyle.labs.danjiangsunseeker.domain.premium

import studio.freestyle.labs.danjiangsunseeker.data.settings.PremiumStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 付費功能 (月相 / 潮汐) 的存取閘道 — 全 App 唯一判斷「是否解鎖」的來源。
 *
 * 設計目的：讓 UI/ViewModel 只依賴此抽象，不直接碰「解鎖從哪來」。
 *
 * 現況：直接代理 [PremiumStore] 的內部開關。
 * TODO(billing): 日後接 Google Play Billing 時，將 [isPremium] 改為
 *   「內部開關 OR 已購買」之合併 Flow (例如 `combine(store.unlocked, billing.purchased) { a, b -> a || b }`)，
 *   呼叫端 (各 ViewModel / 畫面) 完全不需更動。
 */
@Singleton
class PremiumGate @Inject constructor(
    private val store: PremiumStore,
) {
    val isPremium: Flow<Boolean> = store.unlocked
}
