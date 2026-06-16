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
 * PremiumGate 整合「永久付費」與「每頁看廣告暫時解鎖」：
 *  - isPremium(page) = 已付費 OR 該頁暫時解鎖未到期（每頁分開計算）
 *  - isAnyUnlocked   = 已付費 OR 任一頁暫時解鎖未到期（供地圖 / AR）
 *  - isPaid          = 已付費（決定是否移除底部廣告）
 */
class PremiumGateTest {

    private fun store(paid: Boolean, temp: Map<PremiumPage, Long>): PremiumStore = mockk {
        every { this@mockk.paid } returns flowOf(paid)
        every { tempUnlockUntil } returns flowOf(
            PremiumPage.entries.associateWith { temp[it] ?: 0L },
        )
    }

    @Test
    fun `paid unlocks every page and removes ads`() = runTest {
        val gate = PremiumGate(store(paid = true, temp = emptyMap()))
        assertThat(gate.isPremium(PremiumPage.CALENDAR).first()).isTrue()
        assertThat(gate.isAnyUnlocked.first()).isTrue()
        assertThat(gate.isPaid.first()).isTrue()
    }

    @Test
    fun `locked when nothing paid or unlocked`() = runTest {
        val gate = PremiumGate(store(paid = false, temp = emptyMap()))
        assertThat(gate.isPremium(PremiumPage.HOTSPOTS).first()).isFalse()
        assertThat(gate.isAnyUnlocked.first()).isFalse()
        assertThat(gate.isPaid.first()).isFalse()
    }

    @Test
    fun `ad unlock is per-page and does not grant isPaid`() = runTest {
        val future = System.currentTimeMillis() + 60_000L
        val gate = PremiumGate(store(paid = false, temp = mapOf(PremiumPage.CALENDAR to future)))
        // 解鎖的那頁可用
        assertThat(gate.isPremium(PremiumPage.CALENDAR).first()).isTrue()
        // 其他頁仍鎖定
        assertThat(gate.isPremium(PremiumPage.HOTSPOTS).first()).isFalse()
        // 任一頁解鎖 → 地圖/AR 視為解鎖
        assertThat(gate.isAnyUnlocked.first()).isTrue()
        // 但未移除廣告
        assertThat(gate.isPaid.first()).isFalse()
    }

    @Test
    fun `expired ad unlock does not grant access`() = runTest {
        val past = System.currentTimeMillis() - 60_000L
        val gate = PremiumGate(store(paid = false, temp = mapOf(PremiumPage.SIMULATOR to past)))
        assertThat(gate.isPremium(PremiumPage.SIMULATOR).first()).isFalse()
        assertThat(gate.isAnyUnlocked.first()).isFalse()
    }
}
