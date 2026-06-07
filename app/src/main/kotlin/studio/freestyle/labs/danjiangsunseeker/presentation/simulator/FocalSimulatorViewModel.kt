package studio.freestyle.labs.danjiangsunseeker.presentation.simulator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.freestyle.labs.danjiangsunseeker.data.astro.MoonCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.data.astro.SunCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.data.astro.TideDataSource
import studio.freestyle.labs.danjiangsunseeker.data.hotspot.CustomHotspotStore
import studio.freestyle.labs.danjiangsunseeker.data.settings.TowerTargetStore
import studio.freestyle.labs.danjiangsunseeker.data.sensors.LocationProvider
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.DefaultHotspots
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import studio.freestyle.labs.danjiangsunseeker.domain.model.LunarPhase
import studio.freestyle.labs.danjiangsunseeker.domain.model.TideInfo
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.SensorSpec
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.SimulateFocalLengthUseCase
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.TowerTargetSunResolver
import studio.freestyle.labs.danjiangsunseeker.domain.premium.PremiumGate
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
    private val moonCalc: MoonCalcDataSource,
    private val locationProvider: LocationProvider,
    private val customHotspotStore: CustomHotspotStore,
    private val towerTargetStore: TowerTargetStore,
    private val targetSunResolver: TowerTargetSunResolver,
    private val tideDataSource: TideDataSource,
    private val premiumGate: PremiumGate,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(FocalSimulatorState())
    val state: StateFlow<FocalSimulatorState> = _state.asStateFlow()

    private var customHotspots: List<Hotspot> = emptyList()

    // 潮汐只與日期有關（單一測站），依日期快取避免每次拖滑桿都重算。
    private var cachedTideDate: LocalDate? = null
    private var cachedTide: TideInfo? = null

    // ── 從熱點縮圖 / 日曆跳轉過來的初始參數（空字串表示無跳轉）──────────────
    private val jumpHotspotId: String = savedStateHandle["hotspotId"] ?: ""
    private val jumpDate: LocalDate? = (savedStateHandle.get<String>("date") ?: "")
        .takeIf { it.isNotEmpty() }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    private val jumpTarget: TowerTarget? = (savedStateHandle.get<String>("towerTarget") ?: "")
        .takeIf { it.isNotEmpty() }
        ?.let { runCatching { TowerTarget.valueOf(it) }.getOrNull() }
    // 從熱點縮圖 / 日曆帶入的太陽/月亮選擇（月亮為付費功能，鎖定時忽略）
    private val jumpBody: CelestialBody? = (savedStateHandle.get<String>("body") ?: "")
        .takeIf { it.isNotEmpty() }
        ?.let { runCatching { CelestialBody.valueOf(it) }.getOrNull() }

    private val hasJumpArgs = jumpHotspotId.isNotEmpty() || jumpDate != null || jumpTarget != null
    private var jumpHotspotApplied = false
    private var jumpBodyApplied = false

    init {
        // 若有跳轉日期，立刻覆蓋預設日期
        if (jumpDate != null) {
            _state.value = _state.value.copy(date = jumpDate)
        }

        // 監聽自訂熱點，更新選單清單
        customHotspotStore.hotspots
            .onEach { customs ->
                customHotspots = customs
                val merged = mergedHotspots()
                // 第一次載入且有跳轉熱點 → 套用
                if (!jumpHotspotApplied && jumpHotspotId.isNotEmpty()) {
                    jumpHotspotApplied = true
                    val jumpHotspot = merged.find { it.id == jumpHotspotId }
                    if (jumpHotspot != null) {
                        _state.value = _state.value.copy(
                            mergedHotspots = merged,
                            hotspot = jumpHotspot,
                            observer = jumpHotspot.position,
                        )
                        viewModelScope.launch { resetTimeToSunset() }
                        return@onEach
                    }
                }
                _state.value = _state.value.copy(mergedHotspots = merged)
            }
            .launchIn(viewModelScope)

        towerTargetStore.target
            .onEach { storeTarget ->
                // 跳轉參數優先；沒有跳轉才用 store 的值
                val target = jumpTarget ?: storeTarget
                _state.value = _state.value.copy(towerTarget = target)
                viewModelScope.launch { resetTimeToSunset() }
            }
            .launchIn(viewModelScope)

        premiumGate.isPremium
            .onEach { unlocked ->
                // 鎖定時強制回到太陽，避免殘留在月亮模式；解鎖時若有跳轉帶入的天體選擇，套用一次。
                val body = when {
                    !unlocked -> CelestialBody.SUN
                    jumpBody != null && !jumpBodyApplied -> {
                        jumpBodyApplied = true
                        jumpBody
                    }
                    else -> _state.value.body
                }
                _state.value = _state.value.copy(premiumUnlocked = unlocked, body = body)
                recompute()
            }
            .launchIn(viewModelScope)

        // 有跳轉熱點時不自動切到 GPS，避免覆蓋跳轉的地點
        loadGps(autoSelect = !hasJumpArgs)
        viewModelScope.launch { resetTimeToSunset() }
    }

    /** 切換模擬天體（太陽 / 月亮）。月亮為付費功能，鎖定時忽略。 */
    fun setBody(body: CelestialBody) {
        if (body == CelestialBody.MOON && !_state.value.premiumUnlocked) return
        // 切回太陽時，把可能落在隔日的時間夾回當日範圍
        val maxMin = if (body == CelestialBody.MOON) MOON_MAX_MINUTE else 24 * 60 - 1
        _state.value = _state.value.copy(
            body = body,
            timeMinuteOfDay = _state.value.timeMinuteOfDay.coerceAtMost(maxMin),
        )
        recompute()
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

    fun setSensor(spec: SensorSpec) {
        _state.value = _state.value.copy(sensor = spec)
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

    fun setTowerTarget(target: TowerTarget) {
        viewModelScope.launch { towerTargetStore.setTarget(target) }
    }

    /** 設定「自午夜起算的分鐘」。太陽模式上限 23:59；月亮模式可到隔日 06:00 (MOON_MAX_MINUTE)。 */
    fun setMinuteOfDay(minute: Int) {
        val maxMin = if (_state.value.body == CelestialBody.MOON) MOON_MAX_MINUTE else 24 * 60 - 1
        _state.value = _state.value.copy(timeMinuteOfDay = minute.coerceIn(0, maxMin))
        recompute()
    }

    private suspend fun resetTimeToSunset() {
        val s = _state.value
        val events = withContext(Dispatchers.Default) {
            sunCalc.dailyEvents(s.date, s.observer)
        }
        val targetEvent = withContext(Dispatchers.Default) {
            targetSunResolver.resolve(s.date, s.observer, s.towerTarget)
        }
        val defaultMinute = targetEvent.time
            ?.toLocalTime()
            ?.let { it.hour * 60 + it.minute }
            ?: events.sunset
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

        // 計算選定時刻天體位置。用「當日午夜 + N 分鐘」建構，timeMinuteOfDay ≥ 1440 時
        // 自動跨到隔日（月亮模式可到隔日 06:00）。
        val selectedTime = s.date.atStartOfDay(ZoneId.of("Asia/Taipei"))
            .plusMinutes(s.timeMinuteOfDay.toLong())
        // 依選定天體取方位/仰角：太陽用 sunCalc、月亮用 moonCalc（付費）。
        fun bodyAzAlt(t: ZonedDateTime): Pair<Double, Double> =
            if (s.body == CelestialBody.MOON) {
                val m = moonCalc.moonPositionAt(t, s.observer)
                m.azimuthDegrees to m.altitudeDegrees
            } else {
                val p = sunCalc.positionAt(t, s.observer)
                p.azimuthDegrees to p.altitudeDegrees
            }

        // 月相亮面比例 / 盈虧 / 月象（僅月亮模式需要，供 Canvas 畫月相圖案與下方文字顯示月象+亮面%）
        val (moonFrac, moonWaxing) = if (s.body == CelestialBody.MOON) {
            moonCalc.illumination(s.date)
        } else {
            s.moonFractionLit to s.moonWaxing
        }
        val moonPhase = if (s.body == CelestialBody.MOON) {
            moonCalc.classify(moonFrac, moonWaxing)
        } else {
            s.moonPhase
        }
        // 月亮模式時一併顯示當日潮汐（與月相一起呈現；依日期快取）
        val tide = if (s.body == CelestialBody.MOON) {
            if (cachedTideDate != s.date) {
                cachedTide = tideDataSource.tidesFor(s.date)
                cachedTideDate = s.date
            }
            cachedTide
        } else {
            null
        }

        val (selAz, selAlt) = bodyAzAlt(selectedTime)
        val sunInFrame = projectSun(
            sunAzimuth = selAz,
            sunAltitude = selAlt,
            opticalAxisAltitudeDeg = opticalAxisAltitudeDeg,
            observerToTowerBearing = observerToTowerBearing,
            fovH = fovH,
            fovV = fovV,
        )

        // 軌跡 (前後 60 分鐘，每 5 分鐘一點)
        val trail = (-12..12).map { step ->
            val t = selectedTime.plusMinutes((step * 5).toLong())
            val (az, alt) = bodyAzAlt(t)
            projectSun(
                sunAzimuth = az,
                sunAltitude = alt,
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
            sunAltitudeDegrees = selAlt,
            sunAzimuthDegrees = selAz,
            moonFractionLit = moonFrac,
            moonWaxing = moonWaxing,
            moonPhase = moonPhase,
            tideInfo = tide,
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

/** 模擬天體：太陽 / 月亮。 */
enum class CelestialBody { SUN, MOON }

/** 「目前位置」虛擬熱點：由 GPS 動態更新 observer */
val GPS_HOTSPOT = Hotspot(
    id = GPS_HOTSPOT_ID,
    nameRes = null,
    customName = "📍 目前位置",
    position = GeoPoint(BridgeTower.LATITUDE, BridgeTower.LONGITUDE),
)

data class FocalSimulatorState(
    val sensor: SensorSpec = SensorSpec.FULL_FRAME,
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
    /** 月亮亮面比例 0..1（月亮模式時供 Canvas 畫出月相圖案）。 */
    val moonFractionLit: Double = 1.0,
    /** 月亮盈虧：true = 盈（亮面在右）、false = 虧（亮面在左）。 */
    val moonWaxing: Boolean = true,
    /** 月象（八相之一；月亮模式時供下方文字顯示月象名稱）。 */
    val moonPhase: LunarPhase = LunarPhase.FULL,
    /** 當日潮汐（月亮模式時與月相一起顯示；其餘為 null）。 */
    val tideInfo: TideInfo? = null,
    val towerTarget: TowerTarget = TowerTarget.UpperY,
    /** 模擬天體：太陽 / 月亮（月亮為付費功能）。 */
    val body: CelestialBody = CelestialBody.SUN,
    /** 月相/潮汐付費功能是否解鎖（決定是否顯示月亮切換）。 */
    val premiumUnlocked: Boolean = false,
    /** 八里端（主跨 450m）是否在畫面左側；由觀察者方位動態計算 */
    val baliIsOnLeft: Boolean = true,
    /** 八里跨度（450m）在畫面中的寬度比例；隨焦段縮放 */
    val baliSpanFrac: Double = 0.40,
    /** 淡水跨度（175m）在畫面中的寬度比例；隨焦段縮放 */
    val tamsuilSpanFrac: Double = 0.16,
) {
    val selectedTimeLabel: String
        get() {
            val h = (timeMinuteOfDay / 60) % 24
            val m = timeMinuteOfDay % 60
            // 月亮模式可跨夜到隔日；>= 24:00 標示「隔日」
            val prefix = if (timeMinuteOfDay >= 24 * 60) "隔日 " else ""
            return prefix + "%02d:%02d".format(h, m)
        }
}

/** 月亮模式時間軸下限：00:00（當日午夜）。 */
internal const val MOON_MIN_MINUTE = 0
/** 月亮模式時間軸上限：隔日 07:00（= 31:00），涵蓋整夜到隔日清晨的月亮位置。 */
internal const val MOON_MAX_MINUTE = 31 * 60

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
