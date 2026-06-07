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
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.ScanMoonCalendarUseCase
import studio.freestyle.labs.danjiangsunseeker.domain.premium.PremiumGate
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
    private val scanMoonCalendar: ScanMoonCalendarUseCase,
    private val customHotspotStore: CustomHotspotStore,
    private val towerTargetStore: TowerTargetStore,
    private val premiumGate: PremiumGate,
) : ViewModel() {

    private val _state = MutableStateFlow(GoldenCalendarState())
    val state: StateFlow<GoldenCalendarState> = _state.asStateFlow()

    private var customHotspots: List<Hotspot> = emptyList()

    // 以最寬容差 (MAX_TOLERANCE) 掃描一次的完整結果快取；切換容差時只需重新過濾，不必重算天文。
    // 容差只是篩選門檻，不影響任何天文計算 — 重掃完全是浪費。
    private var scannedAll: List<GoldenDate> = emptyList()
    private var scannedTarget: TowerTarget? = null
    private var scannedMode: CalendarMode? = null

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
        premiumGate.isPremium
            .onEach { unlocked ->
                if (unlocked != _state.value.premiumUnlocked) {
                    // 鎖定時強制回到太陽模式
                    val mode = if (unlocked) _state.value.mode else CalendarMode.SUN
                    _state.value = _state.value.copy(premiumUnlocked = unlocked, mode = mode)
                    fullScan(_state.value.toleranceDegrees)
                }
            }
            .launchIn(viewModelScope)
    }

    fun setTolerance(toleranceDeg: Double) {
        // 同一目標+模式、且要求容差不超過已掃描的最寬容差 → 直接過濾快取，瞬間完成。
        if (scannedTarget == _state.value.towerTarget && scannedMode == _state.value.mode &&
            toleranceDeg <= MAX_TOLERANCE
        ) {
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

    /** 切換太陽 / 月亮日曆模式。月亮為付費功能，鎖定時忽略。 */
    fun setMode(mode: CalendarMode) {
        if (mode == CalendarMode.MOON && !_state.value.premiumUnlocked) return
        if (mode == _state.value.mode) return
        _state.value = _state.value.copy(mode = mode)
        fullScan(_state.value.toleranceDegrees)
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
            val mode = _state.value.mode
            val premium = _state.value.premiumUnlocked
            // 一律以最寬容差掃描並快取，之後切換較窄容差只需過濾。
            val all = withContext(Dispatchers.Default) {
                if (mode == CalendarMode.MOON) {
                    scanMoonCalendar(
                        fromDate = today,
                        days = 365,
                        maxOffsetDegrees = MAX_TOLERANCE,
                        hotspots = hotspots,
                        target = target,
                        includeTide = premium,
                    )
                } else {
                    scanCalendar(
                        fromDate = today,
                        days = 365,
                        maxOffsetDegrees = MAX_TOLERANCE,
                        hotspots = hotspots,
                        target = target,
                        includeTide = premium,
                    )
                }
            }
            scannedAll = all
            scannedTarget = target
            scannedMode = mode
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

/** 日曆模式：夕陽穿塔 / 月亮穿塔。 */
enum class CalendarMode { SUN, MOON }

data class GoldenCalendarState(
    val loading: Boolean = true,
    val toleranceDegrees: Double = 2.0,
    val dates: List<GoldenDate> = emptyList(),
    val towerTarget: TowerTarget = TowerTarget.UpperY,
    /** 太陽 / 月亮模式（月亮為付費功能）。 */
    val mode: CalendarMode = CalendarMode.SUN,
    /** 月相/潮汐付費功能是否解鎖。 */
    val premiumUnlocked: Boolean = false,
)
