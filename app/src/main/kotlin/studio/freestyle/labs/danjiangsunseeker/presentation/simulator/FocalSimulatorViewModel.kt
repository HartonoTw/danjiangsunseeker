package studio.freestyle.labs.danjiangsunseeker.presentation.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.freestyle.labs.danjiangsunseeker.data.astro.SunCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.data.hotspot.CustomHotspotStore
import studio.freestyle.labs.danjiangsunseeker.data.sensors.LocationProvider
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.DefaultHotspots
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.SensorSpec
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.SimulateFocalLengthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sin

/** 虛擬 ID，代表「使用 GPS 目前位置」 */
const val GPS_HOTSPOT_ID = "current_gps"

@HiltViewModel
class FocalSimulatorViewModel @Inject constructor(
    private val simulate: SimulateFocalLengthUseCase,
    private val sunCalc: SunCalcDataSource,
    private val locationProvider: LocationProvider,
    private val customHotspotStore: CustomHotspotStore,
) : ViewModel() {

    private val _state = MutableStateFlow(FocalSimulatorState())
    val state: StateFlow<FocalSimulatorState> = _state.asStateFlow()

    private var customHotspots: List<Hotspot> = emptyList()

    init {
        // 監聽自訂熱點，更新選單清單
        customHotspotStore.hotspots
            .onEach { customs ->
                customHotspots = customs
                _state.value = _state.value.copy(mergedHotspots = mergedHotspots())
            }
            .launchIn(viewModelScope)

        // 自動嘗試取一次 GPS（若權限已授過會成功；沒授過會靜默失敗，後續由 [loadGps] 觸發重試）
        loadGps(autoSelect = true)
        viewModelScope.launch { resetTimeToSunset() }
    }

    /**
     * 取得 GPS 目前位置。
     * @param autoSelect 拿到 GPS 後是否自動把 hotspot 切換到「目前位置」。
     *                   init 階段 true（首次自動定位）；之後使用者手動點選時 false（避免覆蓋使用者選擇）。
     */
    fun loadGps(autoSelect: Boolean = false) {
        viewModelScope.launch {
            runCatching {
                val loc = locationProvider.locationUpdates(intervalMillis = 2_000L)
                    .catch { /* 無權限或無 GPS，忽略 */ }
                    .first()
                val gpsPoint = GeoPoint(loc.latitude, loc.longitude, loc.altitude)
                _state.value = if (autoSelect) {
                    _state.value.copy(
                        gpsPoint = gpsPoint,
                        hotspot = GPS_HOTSPOT,
                        observer = gpsPoint,
                    )
                } else {
                    // 只更新 gpsPoint；若使用者目前選的就是 GPS_HOTSPOT，順便更新 observer
                    val s = _state.value
                    if (s.hotspot.id == GPS_HOTSPOT_ID) {
                        s.copy(gpsPoint = gpsPoint, observer = gpsPoint)
                    } else {
                        s.copy(gpsPoint = gpsPoint)
                    }
                }
                resetTimeToSunset()
            }
        }
    }

    private fun mergedHotspots(): List<Hotspot> {
        val customsById = customHotspots.associateBy { it.id }
        val defaults = DefaultHotspots.ALL.map { customsById[it.id] ?: it }
        val pureCustoms = customHotspots.filter { c -> DefaultHotspots.ALL.none { it.id == c.id } }
        return defaults + pureCustoms
    }

    fun setSensor(name: String) {
        _state.value = _state.value.copy(
            sensorName = name,
            sensor = SensorSpec.ALL.first { it.displayName == name },
        )
        recompute()
    }

    fun setFocalLength(mm: Double) {
        _state.value = _state.value.copy(focalLengthMm = mm)
        recompute()
    }

    fun setHotspot(hotspot: Hotspot) {
        val observer = if (hotspot.id == GPS_HOTSPOT_ID) {
            _state.value.gpsPoint ?: hotspot.position
        } else {
            hotspot.position
        }
        _state.value = _state.value.copy(hotspot = hotspot, observer = observer)
        viewModelScope.launch { resetTimeToSunset() }
    }

    fun setDate(date: LocalDate) {
        _state.value = _state.value.copy(date = date)
        viewModelScope.launch { resetTimeToSunset() }
    }

    /** 設定一天中的「分鐘 since midnight」。例如 18:30 → 1110。 */
    fun setMinuteOfDay(minute: Int) {
        _state.value = _state.value.copy(timeMinuteOfDay = minute.coerceIn(0, 24 * 60 - 1))
        recompute()
    }

    private suspend fun resetTimeToSunset() {
        val s = _state.value
        val events = withContext(Dispatchers.Default) {
            sunCalc.dailyEvents(s.date, s.observer)
        }
        val defaultMinute = events.sunset
            ?.toLocalTime()
            ?.let { it.hour * 60 + it.minute }
            ?: (17 * 60 + 30)
        _state.value = _state.value.copy(
            sunsetTime = events.sunset?.toLocalTime(),
            timeMinuteOfDay = defaultMinute,
        )
        recompute()
    }

    private fun recompute() {
        val s = _state.value
        val baseSim = simulate(
            observer = s.observer,
            focalLengthMm = s.focalLengthMm,
            sensor = s.sensor,
        )

        val geodesic = Geodesy.inverse(s.observer, BridgeTower.position)
        val distance = geodesic.distanceMeters.coerceAtLeast(1.0)
        val observerToTowerBearing = geodesic.initialBearingDegrees

        val towerBaseAltitudeDeg = Math.toDegrees(
            atan((BridgeTower.BASE_ELEVATION_M - s.observer.elevationMeters) / distance)
        )
        val opticalAxisAltitudeDeg = if (towerBaseAltitudeDeg < -1.0) {
            towerBaseAltitudeDeg / 2.0
        } else {
            0.0
        }

        val fovH = baseSim.horizontalFovDegrees
        val fovV = baseSim.verticalFovDegrees

        val towerHalfWidthFrac = baseSim.towerWidthFractionOfFrame / 2.0
        val towerTopAltDeg = Math.toDegrees(atan((BridgeTower.TOWER_TIP_ELEVATION_M - s.observer.elevationMeters) / distance))
        val towerBottomAltDeg = Math.toDegrees(atan((BridgeTower.BASE_ELEVATION_M - s.observer.elevationMeters) / distance))
        val deckAltDeg = Math.toDegrees(atan((BridgeTower.DECK_ELEVATION_M - s.observer.elevationMeters) / distance))
        val waterAltDeg = Math.toDegrees(atan((0.0 - s.observer.elevationMeters) / distance))
        val towerTopYFrac = 0.5 - (towerTopAltDeg - opticalAxisAltitudeDeg) / fovV
        val towerBottomYFrac = 0.5 - (towerBottomAltDeg - opticalAxisAltitudeDeg) / fovV
        val deckYFrac = 0.5 - (deckAltDeg - opticalAxisAltitudeDeg) / fovV
        val waterYFrac = 0.5 - (waterAltDeg - opticalAxisAltitudeDeg) / fovV
        val horizonYFrac = 0.5 + opticalAxisAltitudeDeg / fovV

        // ── 橋樑方位判斷 ──────────────────────────────────────────────
        // 從觀察者看向主塔的方位 (observerToTowerBearing) 與八里端方位的差值：
        // 負 → 八里在畫面左側；正 → 八里在畫面右側
        val baliDelta = Geodesy.signedAzimuthDelta(
            BridgeTower.BALI_BEARING_DEG, observerToTowerBearing,
        )
        val baliIsOnLeft = baliDelta < 0

        // ── 跨度在畫面中的比例（考慮橋軸與視線的夾角 + 焦段放大）────
        // 橋軸與觀察者視線的夾角（越接近 90° 表示橋越「橫」在畫面中）
        val bridgeAngleRad = Math.toRadians(abs(baliDelta))
        val sinAngle = sin(bridgeAngleRad).coerceIn(0.0, 1.0)

        // 各跨度的視角（弧度），再轉成畫面寬度比例
        val baliAngDeg = Math.toDegrees(atan(BridgeTower.MAIN_SPAN_M * sinAngle / distance))
        val tamsuilAngDeg = Math.toDegrees(atan(BridgeTower.SIDE_SPAN_M * sinAngle / distance))
        val baliSpanFrac = if (fovH > 0) (baliAngDeg / fovH).coerceIn(0.04, 0.90) else 0.40
        val tamsuilSpanFrac = if (fovH > 0) (tamsuilAngDeg / fovH).coerceIn(0.02, 0.90) else 0.16

        // 計算選定時刻太陽位置
        val selectedTime = ZonedDateTime.of(
            s.date,
            LocalTime.of(s.timeMinuteOfDay / 60, s.timeMinuteOfDay % 60),
            ZoneId.of("Asia/Taipei"),
        )
        val sunPos = sunCalc.positionAt(selectedTime, s.observer)
        val sunInFrame = projectSun(
            sunAzimuth = sunPos.azimuthDegrees,
            sunAltitude = sunPos.altitudeDegrees,
            opticalAxisAltitudeDeg = opticalAxisAltitudeDeg,
            observerToTowerBearing = observerToTowerBearing,
            fovH = fovH,
            fovV = fovV,
        )

        // 軌跡 (前後 60 分鐘，每 5 分鐘一點)
        val trail = (-12..12).map { step ->
            val t = selectedTime.plusMinutes((step * 5).toLong())
            val p = sunCalc.positionAt(t, s.observer)
            projectSun(
                sunAzimuth = p.azimuthDegrees,
                sunAltitude = p.altitudeDegrees,
                opticalAxisAltitudeDeg = opticalAxisAltitudeDeg,
                observerToTowerBearing = observerToTowerBearing,
                fovH = fovH,
                fovV = fovV,
            ).copy(timeOffsetMinutes = step * 5)
        }

        _state.value = s.copy(
            distanceKm = baseSim.distanceToTowerMeters / 1000.0,
            horizontalFovDegrees = fovH,
            verticalFovDegrees = fovV,
            towerLeftFrac = 0.5 - towerHalfWidthFrac,
            towerRightFrac = 0.5 + towerHalfWidthFrac,
            towerTopYFrac = towerTopYFrac,
            towerBottomYFrac = towerBottomYFrac,
            deckYFrac = deckYFrac.coerceIn(-1.0, 2.0),
            waterYFrac = waterYFrac.coerceIn(-1.0, 2.0),
            horizonYFrac = horizonYFrac.coerceIn(-1.0, 2.0),
            sunRadiusFrac = baseSim.sunWidthFractionOfFrame / 2.0,
            sun = sunInFrame,
            sunTrail = trail,
            sunAltitudeDegrees = sunPos.altitudeDegrees,
            sunAzimuthDegrees = sunPos.azimuthDegrees,
            recommendation = baseSim.recommendation,
            baliIsOnLeft = baliIsOnLeft,
            baliSpanFrac = baliSpanFrac,
            tamsuilSpanFrac = tamsuilSpanFrac,
        )
    }

    private fun projectSun(
        sunAzimuth: Double,
        sunAltitude: Double,
        opticalAxisAltitudeDeg: Double,
        observerToTowerBearing: Double,
        fovH: Double,
        fovV: Double,
    ): SunFramePosition {
        val hOffsetDeg = Geodesy.signedAzimuthDelta(sunAzimuth, observerToTowerBearing)
        val vOffsetDeg = sunAltitude - opticalAxisAltitudeDeg
        val xFrac = 0.5 + hOffsetDeg / fovH
        val yFrac = 0.5 - vOffsetDeg / fovV
        val inFrameH = hOffsetDeg in -(fovH / 2)..(fovH / 2)
        val inFrameV = vOffsetDeg in -(fovV / 2)..(fovV / 2)
        return SunFramePosition(
            xFrac = xFrac,
            yFrac = yFrac,
            inFrame = inFrameH && inFrameV,
            offFrameLeft = hOffsetDeg < -fovH / 2,
            offFrameRight = hOffsetDeg > fovH / 2,
            offFrameTop = vOffsetDeg > fovV / 2,
            offFrameBottom = vOffsetDeg < -fovV / 2,
            altitudeDegrees = sunAltitude,
        )
    }
}

/** 「目前位置」虛擬熱點：由 GPS 動態更新 observer */
val GPS_HOTSPOT = Hotspot(
    id = GPS_HOTSPOT_ID,
    nameRes = null,
    customName = "📍 目前位置",
    position = GeoPoint(BridgeTower.LATITUDE, BridgeTower.LONGITUDE),
)

data class FocalSimulatorState(
    val sensor: SensorSpec = SensorSpec.FULL_FRAME,
    val sensorName: String = SensorSpec.FULL_FRAME.displayName,
    val focalLengthMm: Double = 50.0,
    val hotspot: Hotspot = DefaultHotspots.TAMSUI_FERRY,
    val observer: GeoPoint = DefaultHotspots.TAMSUI_FERRY.position,
    val gpsPoint: GeoPoint? = null,          // 最新 GPS 讀數
    val mergedHotspots: List<Hotspot> = DefaultHotspots.ALL, // 含自訂熱點的完整清單
    val date: LocalDate = LocalDate.now(ZoneId.of("Asia/Taipei")),
    val timeMinuteOfDay: Int = 17 * 60 + 30,
    val sunsetTime: LocalTime? = null,
    val distanceKm: Double = 0.0,
    val horizontalFovDegrees: Double = 0.0,
    val verticalFovDegrees: Double = 0.0,
    val towerLeftFrac: Double = 0.4,
    val towerRightFrac: Double = 0.6,
    val towerTopYFrac: Double = 0.1,
    val towerBottomYFrac: Double = 0.9,
    val deckYFrac: Double = 0.55,
    val waterYFrac: Double = 0.6,
    val horizonYFrac: Double = 0.5,
    val sunRadiusFrac: Double = 0.05,
    val sun: SunFramePosition = SunFramePosition(0.5, 0.5),
    val sunTrail: List<SunFramePosition> = emptyList(),
    val sunAltitudeDegrees: Double = 0.0,
    val sunAzimuthDegrees: Double = 0.0,
    val recommendation: String = "",
    /** 八里端（主跨 450m）是否在畫面左側；由觀察者方位動態計算 */
    val baliIsOnLeft: Boolean = true,
    /** 八里跨度（450m）在畫面中的寬度比例；隨焦段縮放 */
    val baliSpanFrac: Double = 0.40,
    /** 淡水跨度（175m）在畫面中的寬度比例；隨焦段縮放 */
    val tamsuilSpanFrac: Double = 0.16,
) {
    val selectedTimeLabel: String
        get() = "%02d:%02d".format(timeMinuteOfDay / 60, timeMinuteOfDay % 60)
}

/** 太陽在 Canvas 中的位置 (以畫面寬高 0..1 fraction 表示)。 */
data class SunFramePosition(
    val xFrac: Double,
    val yFrac: Double,
    val inFrame: Boolean = true,
    val offFrameLeft: Boolean = false,
    val offFrameRight: Boolean = false,
    val offFrameTop: Boolean = false,
    val offFrameBottom: Boolean = false,
    val altitudeDegrees: Double = 0.0,
    val timeOffsetMinutes: Int = 0,
)
