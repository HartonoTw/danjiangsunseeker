package studio.freestyle.labs.danjiangsunseeker.domain.premium

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import studio.freestyle.labs.danjiangsunseeker.data.settings.PremiumStore

/**
 * PremiumGate 目前只是 PremiumStore.unlocked 的代理；驗證它如實反映解鎖狀態。
 * 日後接 Billing 時，這裡可加入「內部開關 OR 已購買」的合併邏輯測試。
 */
class PremiumGateTest {

    @Test
    fun `isPremium reflects unlocked store state`() = runTest {
        val store: PremiumStore = mockk()
        every { store.unlocked } returns flowOf(true)
        val gate = PremiumGate(store)
        assertThat(gate.isPremium.first()).isTrue()
    }

    @Test
    fun `isPremium is false when store is locked`() = runTest {
        val store: PremiumStore = mockk()
        every { store.unlocked } returns flowOf(false)
        val gate = PremiumGate(store)
        assertThat(gate.isPremium.first()).isFalse()
    }
}
