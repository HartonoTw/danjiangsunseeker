package studio.freestyle.labs.danjiangsunseeker.presentation.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.freestyle.labs.danjiangsunseeker.data.settings.PremiumStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「關於」頁的付費內部開關控制器。
 *
 * 目前以隱藏連點 (版本晶片連點數次) 切換 [PremiumStore] 的解鎖狀態，供內部測試。
 * 日後接 Google Play Billing 後，此切換仍可作測試/促銷解鎖用途。
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    private val premiumStore: PremiumStore,
) : ViewModel() {

    val premiumUnlocked: StateFlow<Boolean> = premiumStore.unlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setUnlocked(value: Boolean) {
        viewModelScope.launch { premiumStore.setUnlocked(value) }
    }
}
