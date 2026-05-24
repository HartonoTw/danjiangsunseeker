package studio.freestyle.labs.danjiangsunseeker.presentation.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.freestyle.labs.danjiangsunseeker.data.astro.SunCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.data.hotspot.CustomHotspotStore
import studio.freestyle.labs.danjiangsunseeker.data.settings.TowerTargetStore
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.DefaultHotspots
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.GoldenLine
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.ComputeGoldenLineUseCase
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.TowerTargetSunResolver
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

@HiltViewModel
class MapViewModel @Inject constructor(
    private val computeGoldenLine: ComputeGoldenLineUseCase,
    private val sunCalc: SunCalcDataSource,
    private val customHotspotStore: CustomHotspotStore,
    private val towerTargetStore: TowerTargetStore,
    private val targetSunResolver: TowerTargetSunResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var customHotspots: List<Hotspot> = emptyList()

    init {
        Log.d(TAG, "init: MapViewModel instance=${System.identityHashCode(this)}")
        // 監聽自訂熱點，有變動時觸發 UI 更新地圖圖層
        customHotspotStore.hotspots
            .onEach { customs ->
                Log.d(TAG, "flow.onEach: customs.size=${customs.size} ids=${customs.map { it.id }}")
                customHotspots = customs
                val merged = mergedHotspots()
                Log.d(TAG, "flow.onEach: merged.size=${merged.size} ids=${merged.map { it.id }}")
                _state.value = _state.value.copy(mergedHotspots = merged)
            }
            .launchIn(viewModelScope)
        towerTargetStore.target
            .onEach { target ->
                _state.value = _state.value.copy(towerTarget = target)
                setDate(_state.value.selectedDate)
            }
            .launchIn(viewModelScope)

        setDate(LocalDate.now(ZoneId.of("Asia/Taipei")))
    }

    private fun mergedHotspots(): List<Hotspot> {
        val customsById = customHotspots.associateBy { it.id }
        val defaults = DefaultHotspots.ALL.map { customsById[it.id] ?: it }
        val pureCustoms = customHotspots.filter { c -> DefaultHotspots.ALL.none { it.id == c.id } }
        val result = defaults + pureCustoms
        Log.d(TAG, "mergedHotspots(): customHotspots.size=${customHotspots.size} defaults.size=${defaults.size} pure.size=${pureCustoms.size} result.size=${result.size}")
        return result
    }

    companion object { private const val TAG = "MapDebug" }

    fun setDate(date: LocalDate) {
        _state.value = _state.value.copy(selectedDate = date, computing = true)
        viewModelScope.launch {
            val (gl, sunsetAz) = withContext(Dispatchers.Default) {
                val gl = computeGoldenLine(date)
                val sunAz = sunCalc.dailyEvents(date, BridgeTower.position).sunsetAzimuthDegrees
                gl to sunAz
            }
            _state.value = _state.value.copy(
                goldenLine = gl,
                sunsetAzimuthAtTower = sunsetAz,
                computing = false,
                tap = _state.value.tap?.let { rebuildTap(it.point) },
            )
        }
    }

    fun onMapTap(latitude: Double, longitude: Double) {
        val point = GeoPoint(latitude, longitude, elevationMeters = 0.0)
        _state.value = _state.value.copy(tap = rebuildTap(point))
    }

    fun clearTap() {
        _state.value = _state.value.copy(tap = null)
    }

    fun setTowerTarget(target: TowerTarget) {
        viewModelScope.launch { towerTargetStore.setTarget(target) }
    }

    private fun rebuildTap(point: GeoPoint): TapAnalysis {
        val geodesic = Geodesy.inverse(point, BridgeTower.position)
        val event = targetSunResolver.resolve(_state.value.selectedDate, point, _state.value.towerTarget)
        val offset = event.azimuthDegrees?.let {
            Geodesy.signedAzimuthDelta(it, geodesic.initialBearingDegrees)
        }
        return TapAnalysis(
            point = point,
            distanceToTowerMeters = geodesic.distanceMeters,
            bearingToTowerDegrees = geodesic.initialBearingDegrees,
            sunsetAzimuthDegrees = event.azimuthDegrees,
            alignmentOffsetDegrees = offset,
            targetTime = event.time,
            towerTarget = _state.value.towerTarget,
        )
    }
}

data class MapUiState(
    val selectedDate: LocalDate = LocalDate.now(ZoneId.of("Asia/Taipei")),
    val goldenLine: GoldenLine? = null,
    val sunsetAzimuthAtTower: Double? = null,
    val computing: Boolean = false,
    val tap: TapAnalysis? = null,
    val mergedHotspots: List<Hotspot> = DefaultHotspots.ALL,
    val towerTarget: TowerTarget = TowerTarget.UpperY,
)

data class TapAnalysis(
    val point: GeoPoint,
    val distanceToTowerMeters: Double,
    val bearingToTowerDegrees: Double,
    val sunsetAzimuthDegrees: Double?,
    val targetTime: java.time.ZonedDateTime?,
    val towerTarget: TowerTarget,
    /**
     * 從點擊位置看，太陽方位相對主塔方位的偏差 (deg, signed)。
     * 正值: 太陽位於主塔右側 (順時針); 負值: 左側。
     */
    val alignmentOffsetDegrees: Double?,
)
