package studio.freestyle.labs.danjiangsunseeker.domain.premium

import studio.freestyle.labs.danjiangsunseeker.data.settings.PremiumStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 付費功能 (月相 / 潮汐) 的存取閘道 — 全 App 唯一判斷「是否解鎖 / 是否已付費」的來源。
 *
 *  - [isPremium]：**某一頁**功能是否可用 = 已付費 **或** 該頁看廣告解鎖尚未到期（每頁分開計算）。
 *  - [isAnyUnlocked]：任一頁已解鎖（或已付費）。供沒有自己解鎖入口的地圖 / AR 使用
 *    （「只要有一頁解鎖就算解鎖」）。
 *  - [isPaid]：是否永久專業版。底部橫幅廣告是否移除只看它（看廣告解鎖不移除廣告）。
 *
 * Note: 以「當下時間 < 到期時間」判斷看廣告解鎖；Flow 只在 store 變動時重新計算，
 *   到期當下不會主動發射 — 下次任何 store 變動或畫面重建時即會反映到期。
 *
 * TODO(billing): 接 Google Play Billing 時，[isPaid] 改為「內部開關 OR 已購買」之合併 Flow，
 *   呼叫端完全不需更動。
 */
@Singleton
class PremiumGate @Inject constructor(
    private val store: PremiumStore,
) {
    val isPaid: Flow<Boolean> = store.paid

    /** 指定頁是否解鎖（已付費 或 該頁看廣告解鎖未到期）。 */
    fun isPremium(page: PremiumPage): Flow<Boolean> =
        combine(store.paid, store.tempUnlockUntil) { paid, until ->
            paid || System.currentTimeMillis() < (until[page] ?: 0L)
        }

    /** 任一頁已解鎖（或已付費）。供地圖 / AR 使用。 */
    val isAnyUnlocked: Flow<Boolean> =
        combine(store.paid, store.tempUnlockUntil) { paid, until ->
            paid || until.values.any { System.currentTimeMillis() < it }
        }
}
