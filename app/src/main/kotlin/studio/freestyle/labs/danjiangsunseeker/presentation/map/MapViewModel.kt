package studio.freestyle.labs.danjiangsunseeker.presentation.map

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.freestyle.labs.danjiangsunseeker.data.astro.MoonCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.data.astro.SunCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.data.astro.TideDataSource
import studio.freestyle.labs.danjiangsunseeker.data.hotspot.CustomHotspotStore
import studio.freestyle.labs.danjiangsunseeker.data.sensors.LocationProvider
import studio.freestyle.labs.danjiangsunseeker.data.settings.TowerTargetStore
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.DefaultHotspots
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.GoldenLine
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import studio.freestyle.labs.danjiangsunseeker.domain.model.MoonInfo
import studio.freestyle.labs.danjiangsunseeker.domain.model.TideInfo
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.ComputeGoldenLineUseCase
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.ComputeMoonGoldenLineUseCase
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.TowerTargetMoonResolver
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.TowerTargetSunResolver
import studio.freestyle.labs.danjiangsunseeker.domain.premium.PremiumGate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import studio.freestyle.labs.danjiangsunseeker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val computeGoldenLine: ComputeGoldenLineUseCase,
    private val computeMoonGoldenLine: ComputeMoonGoldenLineUseCase,
    private val sunCalc: SunCalcDataSource,
    private val moonCalc: MoonCalcDataSource,
    private val tideDataSource: TideDataSource,
    private val customHotspotStore: CustomHotspotStore,
    private val towerTargetStore: TowerTargetStore,
    private val targetSunResolver: TowerTargetSunResolver,
    private val targetMoonResolver: TowerTargetMoonResolver,
    private val locationProvider: LocationProvider,
    private val premiumGate: PremiumGate,
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
        // 地圖無自己的解鎖入口：任一頁解鎖（或已付費）即顯示月相/潮汐/月亮黃金帶。
        premiumGate.isAnyUnlocked
            .onEach { unlocked ->
                if (unlocked != _state.value.premiumUnlocked) {
                    _state.value = _state.value.copy(premiumUnlocked = unlocked)
                    setDate(_state.value.selectedDate)
                }
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

    private var computeJob: Job? = null
    private var pendingDate: LocalDate? = null

    fun setDate(date: LocalDate) {
        // 立即更新日期文字（便宜），重運算放背景。
        _state.value = _state.value.copy(selectedDate = date)
        // 用「conflated latest」取代取消：永遠記住最新要算的日期；
        // 若已有 worker 在跑就不另開，等它算完會自動接最新日期。
        // 這樣每次算完的結果都會落地（線會動），但快速播放時自動略過跟不上的中間日期，
        // 不會像取消那樣每次都被打斷而完全不更新。
        pendingDate = date
        if (computeJob?.isActive == true) return
        computeJob = viewModelScope.launch {
            while (true) {
                val d = pendingDate ?: break
                pendingDate = null
                val premium = _state.value.premiumUnlocked
                val result = withContext(Dispatchers.Default) {
                    val gl = computeGoldenLine(d)
                    val topGl = computeGoldenLine(d, target = TowerTarget.UpperY)
                    val sunAz = sunCalc.dailyEvents(d, BridgeTower.position).sunsetAzimuthDegrees
                    // 月亮黃金帶 / 月相 / 潮汐皆為付費功能；鎖定時為 null。
                    val moonGl = if (premium) computeMoonGoldenLine(d) else null
                    val moon = if (premium) moonCalc.dailyMoon(d, BridgeTower.position) else null
                    val tide = if (premium) tideDataSource.tidesFor(d) else null
                    MapComputeResult(gl, topGl, sunAz, moonGl, moon, tide)
                }
                _state.value = _state.value.copy(
                    goldenLine = result.goldenLine,
                    towerTopGoldenLine = result.towerTopGoldenLine,
                    sunsetAzimuthAtTower = result.sunsetAzimuth,
                    moonGoldenLine = result.moonGoldenLine,
                    moonInfo = result.moonInfo,
                    tideInfo = result.tideInfo,
                    computing = false,
                    tap = _state.value.tap?.let { rebuildTap(it.point) },
                )
            }
        }
    }

    /** 以目前選定日期為基準，往前或往後跳 [days] 天。 */
    fun stepDate(days: Long) {
        setDate(_state.value.selectedDate.plusDays(days))
    }

    fun onMapTap(latitude: Double, longitude: Double) {
        val point = GeoPoint(latitude, longitude, elevationMeters = 0.0)
        _state.value = _state.value.copy(tap = rebuildTap(point))
    }

    fun clearTap() {
        _state.value = _state.value.copy(tap = null)
    }

    fun showAddTapHotspotDialog() {
        val tap = _state.value.tap ?: return
        val point = tap.point
        _state.value = _state.value.copy(
            hotspotDraft = MapHotspotDraft(
                latitude = "%.6f".format(point.latitude),
                longitude = "%.6f".format(point.longitude),
                elevation = point.elevationMeters.toString(),
            ),
        )
    }

    /**
     * 以「地圖中心（準心圓點所指）」座標開啟新增熱點對話框。
     * 由 UI 從相機 target 讀出中心經緯度傳入，解決「移動地圖後存檔仍記到舊點」的問題。
     */
    fun showAddHotspotDialog(latitude: Double, longitude: Double) {
        _state.value = _state.value.copy(
            hotspotDraft = MapHotspotDraft(
                latitude = "%.6f".format(latitude),
                longitude = "%.6f".format(longitude),
            ),
        )
    }

    fun closeHotspotDraft() {
        _state.value = _state.value.copy(hotspotDraft = null)
    }

    fun updateHotspotDraft(field: MapHotspotDraftField, value: String) {
        val draft = _state.value.hotspotDraft ?: return
        _state.value = _state.value.copy(
            hotspotDraft = when (field) {
                MapHotspotDraftField.Name -> draft.copy(name = value, error = null)
                MapHotspotDraftField.Elevation -> draft.copy(elevation = value, error = null)
                MapHotspotDraftField.Description -> draft.copy(description = value, error = null)
            },
        )
    }

    fun saveHotspotDraft() {
        val draft = _state.value.hotspotDraft ?: return
        val lat = draft.latitude.toDoubleOrNull()
        val lon = draft.longitude.toDoubleOrNull()
        val elev = draft.elevation.toDoubleOrNull() ?: 0.0
        if (draft.name.isBlank() || lat == null || lon == null) {
            _state.value = _state.value.copy(
                hotspotDraft = draft.copy(error = context.getString(R.string.msg_enter_name_valid_coords)),
            )
            return
        }
        val hotspot = Hotspot(
            id = "${CustomHotspotStore.ID_PREFIX}${System.currentTimeMillis()}",
            nameRes = null,
            customName = draft.name.trim(),
            position = GeoPoint(lat, lon, elev),
            description = draft.description.trim(),
        )
        viewModelScope.launch {
            runCatching { customHotspotStore.upsert(hotspot) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        hotspotDraft = null,
                        locationMessage = context.getString(R.string.msg_hotspot_added),
                    )
                }
                .onFailure { e ->
                    val current = _state.value.hotspotDraft
                    _state.value = _state.value.copy(
                        hotspotDraft = current?.copy(error = context.getString(R.string.msg_hotspot_add_failed, e.message ?: "")),
                    )
                }
        }
    }

    fun flyToCurrentLocation() {
        if (_state.value.locatingCurrentLocation) return
        if (!locationProvider.hasPermission()) {
            _state.value = _state.value.copy(locationMessage = context.getString(R.string.msg_need_location_permission))
            return
        }

        _state.value = _state.value.copy(locatingCurrentLocation = true, locationMessage = null)
        viewModelScope.launch {
            runCatching {
                withTimeout(10_000L) {
                    withContext(Dispatchers.IO) {
                        locationProvider.locationUpdates(intervalMillis = 1_000L).first()
                    }
                }
            }.onSuccess { location ->
                val point = GeoPoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    elevationMeters = if (location.hasAltitude()) location.altitude else 0.0,
                )
                _state.value = _state.value.copy(
                    currentLocation = point,
                    currentLocationFlyRequest = _state.value.currentLocationFlyRequest + 1,
                    locatingCurrentLocation = false,
                )
            }.onFailure { err ->
                Log.w(TAG, "flyToCurrentLocation failed", err)
                _state.value = _state.value.copy(
                    locatingCurrentLocation = false,
                    locationMessage = context.getString(R.string.msg_location_unavailable),
                )
            }
        }
    }

    fun clearLocationMessage() {
        _state.value = _state.value.copy(locationMessage = null)
    }

    fun setTowerTarget(target: TowerTarget) {
        viewModelScope.launch { towerTargetStore.setTarget(target) }
    }

    private fun rebuildTap(point: GeoPoint): TapAnalysis {
        val geodesic = Geodesy.inverse(point, BridgeTower.position)
        val lowerEvent = targetSunResolver.resolve(_state.value.selectedDate, point, TowerTarget.LowerY)
        val lowerOffset = lowerEvent.azimuthDegrees?.let {
            Geodesy.signedAzimuthDelta(it, geodesic.initialBearingDegrees)
        }
        val upperEvent = targetSunResolver.resolve(_state.value.selectedDate, point, TowerTarget.UpperY)
        val upperOffset = upperEvent.azimuthDegrees?.let {
            Geodesy.signedAzimuthDelta(it, geodesic.initialBearingDegrees)
        }
        val selectedEvent = if (_state.value.towerTarget == TowerTarget.UpperY) upperEvent else lowerEvent
        val selectedOffset = if (_state.value.towerTarget == TowerTarget.UpperY) upperOffset else lowerOffset

        // 月亮穿塔 (付費功能；鎖定時不計算)：從選定點看月亮上升穿越塔頂/塔基的偏差與時間。
        val premium = _state.value.premiumUnlocked
        val moonLowerEvent = if (premium) targetMoonResolver.resolve(_state.value.selectedDate, point, TowerTarget.LowerY) else null
        val moonLowerOffset = moonLowerEvent?.azimuthDegrees?.let {
            Geodesy.signedAzimuthDelta(it, geodesic.initialBearingDegrees)
        }
        val moonUpperEvent = if (premium) targetMoonResolver.resolve(_state.value.selectedDate, point, TowerTarget.UpperY) else null
        val moonUpperOffset = moonUpperEvent?.azimuthDegrees?.let {
            Geodesy.signedAzimuthDelta(it, geodesic.initialBearingDegrees)
        }

        return TapAnalysis(
            point = point,
            distanceToTowerMeters = geodesic.distanceMeters,
            bearingToTowerDegrees = geodesic.initialBearingDegrees,
            sunsetAzimuthDegrees = selectedEvent.azimuthDegrees,
            alignmentOffsetDegrees = selectedOffset,
            targetTime = selectedEvent.time,
            towerTarget = _state.value.towerTarget,
            lowerTargetTime = lowerEvent.time,
            lowerAlignmentOffsetDegrees = lowerOffset,
            upperTargetTime = upperEvent.time,
            upperAlignmentOffsetDegrees = upperOffset,
            moonLowerTime = moonLowerEvent?.time,
            moonLowerAlignmentOffsetDegrees = moonLowerOffset,
            moonUpperTime = moonUpperEvent?.time,
            moonUpperAlignmentOffsetDegrees = moonUpperOffset,
        )
    }
}

/** [MapViewModel.setDate] 背景計算的彙整結果。 */
private data class MapComputeResult(
    val goldenLine: GoldenLine?,
    val towerTopGoldenLine: GoldenLine?,
    val sunsetAzimuth: Double?,
    val moonGoldenLine: GoldenLine?,
    val moonInfo: MoonInfo?,
    val tideInfo: TideInfo?,
)

data class MapUiState(
    val selectedDate: LocalDate = LocalDate.now(ZoneId.of("Asia/Taipei")),
    val goldenLine: GoldenLine? = null,
    val towerTopGoldenLine: GoldenLine? = null,
    val sunsetAzimuthAtTower: Double? = null,
    /** 月亮黃金帶 (付費功能；鎖定時為 null)。 */
    val moonGoldenLine: GoldenLine? = null,
    /** 當日月相 (付費功能；鎖定時為 null)。主塔位置基準。 */
    val moonInfo: MoonInfo? = null,
    /** 當日潮汐 (付費功能；鎖定時為 null)。淡水測站。 */
    val tideInfo: TideInfo? = null,
    val premiumUnlocked: Boolean = false,
    val computing: Boolean = false,
    val tap: TapAnalysis? = null,
    val mergedHotspots: List<Hotspot> = DefaultHotspots.ALL,
    val towerTarget: TowerTarget = TowerTarget.UpperY,
    val currentLocation: GeoPoint? = null,
    val currentLocationFlyRequest: Int = 0,
    val locatingCurrentLocation: Boolean = false,
    val locationMessage: String? = null,
    val hotspotDraft: MapHotspotDraft? = null,
)

data class MapHotspotDraft(
    val name: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val elevation: String = "0.0",
    val description: String = "",
    val error: String? = null,
)

enum class MapHotspotDraftField { Name, Elevation, Description }

data class TapAnalysis(
    val point: GeoPoint,
    val distanceToTowerMeters: Double,
    val bearingToTowerDegrees: Double,
    val sunsetAzimuthDegrees: Double?,
    val targetTime: java.time.ZonedDateTime?,
    val towerTarget: TowerTarget,
    val lowerTargetTime: java.time.ZonedDateTime?,
    val lowerAlignmentOffsetDegrees: Double?,
    val upperTargetTime: java.time.ZonedDateTime?,
    val upperAlignmentOffsetDegrees: Double?,
    /** 月亮上升穿塔基的時間與方位偏差 (付費功能；鎖定或當日無月出時為 null)。 */
    val moonLowerTime: java.time.ZonedDateTime? = null,
    val moonLowerAlignmentOffsetDegrees: Double? = null,
    /** 月亮上升穿塔頂的時間與方位偏差 (付費功能；鎖定或當日無月出時為 null)。 */
    val moonUpperTime: java.time.ZonedDateTime? = null,
    val moonUpperAlignmentOffsetDegrees: Double? = null,
    /**
     * 從點擊位置看，太陽方位相對主塔方位的偏差 (deg, signed)。
     * 正值: 太陽位於主塔右側 (順時針); 負值: 左側。
     */
    val alignmentOffsetDegrees: Double?,
)
