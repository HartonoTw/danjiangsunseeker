package studio.freestyle.labs.danjiangsunseeker.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.freestyle.labs.danjiangsunseeker.data.hotspot.CustomHotspotStore
import studio.freestyle.labs.danjiangsunseeker.data.settings.TowerTargetStore
import studio.freestyle.labs.danjiangsunseeker.domain.model.DefaultHotspots
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.GoldenDate
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.ScanGoldenCalendarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class GoldenCalendarViewModel @Inject constructor(
    private val scanCalendar: ScanGoldenCalendarUseCase,
    private val customHotspotStore: CustomHotspotStore,
    private val towerTargetStore: TowerTargetStore,
) : ViewModel() {

    private val _state = MutableStateFlow(GoldenCalendarState())
    val state: StateFlow<GoldenCalendarState> = _state.asStateFlow()

    private var customHotspots: List<Hotspot> = emptyList()

    // 以最寬容差 (MAX_TOLERANCE) 掃描一次的完整結果快取；切換容差時只需重新過濾，不必重算太陽位置。
    // 容差只是篩選門檻，不影響任何天文計算 — 重掃完全是浪費。
    private var scannedAll: List<GoldenDate> = emptyList()
    private var scannedTarget: TowerTarget? = null

    init {
        customHotspotStore.hotspots
            .onEach { customs ->
                customHotspots = customs
                fullScan(_state.value.toleranceDegrees)
            }
            .launchIn(viewModelScope)
        towerTargetStore.target
            .onEach { target ->
                _state.value = _state.value.copy(towerTarget = target)
                fullScan(_state.value.toleranceDegrees)
            }
            .launchIn(viewModelScope)
    }

    fun setTolerance(toleranceDeg: Double) {
        // 同一塔頂目標、且要求容差不超過已掃描的最寬容差 → 直接過濾快取，瞬間完成。
        if (scannedTarget == _state.value.towerTarget && toleranceDeg <= MAX_TOLERANCE) {
            _state.value = _state.value.copy(
                loading = false,
                toleranceDegrees = toleranceDeg,
                dates = scannedAll.filter { abs(it.alignmentOffsetDegrees) <= toleranceDeg },
            )
        } else {
            fullScan(toleranceDeg)
        }
    }

    fun setTowerTarget(target: TowerTarget) {
        viewModelScope.launch { towerTargetStore.setTarget(target) }
    }

    private fun mergedHotspots(): List<Hotspot> {
        val customsById = customHotspots.associateBy { it.id }
        val defaults = DefaultHotspots.ALL.map { customsById[it.id] ?: it }
        val pureCustoms = customHotspots.filter { c -> DefaultHotspots.ALL.none { it.id == c.id } }
        return defaults + pureCustoms
    }

    private fun fullScan(toleranceDeg: Double) {
        _state.value = _state.value.copy(loading = true, toleranceDegrees = toleranceDeg)
        viewModelScope.launch {
            val today = LocalDate.now(ZoneId.of("Asia/Taipei"))
            val hotspots = mergedHotspots()
            val target = _state.value.towerTarget
            // 一律以最寬容差掃描並快取，之後切換較窄容差只需過濾。
            val all = withContext(Dispatchers.Default) {
                scanCalendar(
                    fromDate = today,
                    days = 365,
                    maxOffsetDegrees = MAX_TOLERANCE,
                    hotspots = hotspots,
                    target = target,
                )
            }
            scannedAll = all
            scannedTarget = target
            _state.value = _state.value.copy(
                loading = false,
                dates = all.filter { abs(it.alignmentOffsetDegrees) <= toleranceDeg },
                toleranceDegrees = toleranceDeg,
            )
        }
    }

    private companion object {
        // 容差篩選器的最大值 (UI chip 上限為 ±5°)；快取以此掃描一次即可服務所有 chip。
        const val MAX_TOLERANCE = 5.0
    }
}

data class GoldenCalendarState(
    val loading: Boolean = true,
    val toleranceDegrees: Double = 2.0,
    val dates: List<GoldenDate> = emptyList(),
    val towerTarget: TowerTarget = TowerTarget.UpperY,
)
