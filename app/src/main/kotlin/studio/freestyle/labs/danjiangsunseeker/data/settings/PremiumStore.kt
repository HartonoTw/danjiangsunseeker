package studio.freestyle.labs.danjiangsunseeker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import studio.freestyle.labs.danjiangsunseeker.domain.premium.PremiumPage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 付費功能 (月相 / 潮汐) 的解鎖狀態，持久化於 DataStore。
 *
 * 兩段式解鎖：
 *  - [paid]：永久專業版（升級後解鎖全部功能 **且** 移除底部橫幅廣告）。
 *  - [tempUnlockUntil]：**每頁分開計算**的「看廣告免費解鎖」到期時間 (epoch millis)；
 *    在此之前該頁功能可用，但**廣告仍會顯示**（只有 [paid] 才移除廣告）。
 *
 * 「是否解鎖功能 / 是否已付費」的判斷集中在
 * [studio.freestyle.labs.danjiangsunseeker.domain.premium.PremiumGate]。
 *
 * TODO(billing): [setPaid] 日後改由 Google Play Billing 的購買回呼驅動。
 */
@Singleton
class PremiumStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val ds = context.premiumDataStore

    /** 永久專業版（已付費）。 */
    val paid: Flow<Boolean> = ds.data.map { prefs ->
        prefs[KEY_PAID] ?: false
    }

    /** 各頁「看廣告免費解鎖」到期時間 (epoch millis)；0 表示未曾解鎖。 */
    val tempUnlockUntil: Flow<Map<PremiumPage, Long>> = ds.data.map { prefs ->
        PremiumPage.entries.associateWith { page -> prefs[keyFor(page)] ?: 0L }
    }

    /** 設定永久專業版狀態。 */
    suspend fun setPaid(value: Boolean) {
        ds.edit { prefs -> prefs[KEY_PAID] = value }
    }

    /** 授予指定頁「看廣告免費解鎖」至指定時刻 (epoch millis)。 */
    suspend fun grantTempUnlock(page: PremiumPage, untilEpochMillis: Long) {
        ds.edit { prefs -> prefs[keyFor(page)] = untilEpochMillis }
    }

    companion object {
        private val KEY_PAID = booleanPreferencesKey("premium_paid")
        private fun keyFor(page: PremiumPage) = longPreferencesKey("premium_temp_unlock_${page.key}")
    }
}

private val Context.premiumDataStore by preferencesDataStore("premium")
