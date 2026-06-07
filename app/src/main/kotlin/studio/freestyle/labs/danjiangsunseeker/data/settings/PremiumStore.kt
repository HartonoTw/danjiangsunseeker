package studio.freestyle.labs.danjiangsunseeker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「付費功能」內部解鎖開關 (執行期、持久化於 DataStore)。
 *
 * 目前由 About 頁隱藏連點切換 (內部測試)。日後改為付費時，此開關仍可保留作
 * 「測試帳號 / 促銷解鎖」用途；正式購買狀態由 [studio.freestyle.labs.danjiangsunseeker.domain.premium.PremiumGate] 整合。
 */
@Singleton
class PremiumStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val ds = context.premiumDataStore

    val unlocked: Flow<Boolean> = ds.data.map { prefs ->
        prefs[KEY_UNLOCKED] ?: false
    }

    suspend fun setUnlocked(value: Boolean) {
        ds.edit { prefs -> prefs[KEY_UNLOCKED] = value }
    }

    companion object {
        private val KEY_UNLOCKED = booleanPreferencesKey("premium_unlocked")
    }
}

private val Context.premiumDataStore by preferencesDataStore("premium")
