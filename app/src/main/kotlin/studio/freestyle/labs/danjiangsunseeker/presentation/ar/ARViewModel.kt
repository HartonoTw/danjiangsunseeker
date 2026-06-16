package studio.freestyle.labs.danjiangsunseeker.presentation.ar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.freestyle.labs.danjiangsunseeker.data.astro.MoonCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.data.astro.SunCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.data.sensors.DeviceOrientation
import studio.freestyle.labs.danjiangsunseeker.data.sensors.DeviceOrientationProvider
import studio.freestyle.labs.danjiangsunseeker.data.sensors.LocationProvider
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import studio.freestyle.labs.danjiangsunseeker.domain.premium.PremiumGate
import studio.freestyle.labs.danjiangsunseeker.R
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.atan

@HiltViewModel
class ARViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orientationProvider: DeviceOrientationProvider,
    private val locationProvider: LocationProvider,
    private val sunCalc: SunCalcDataSource,
    private val moonCalc: MoonCalcDataSource,
    private val premiumGate: PremiumGate,
) : ViewModel() {

    private val _state = MutableStateFlow(ARState())
    val state: StateFlow<ARState> = _state.asStateFlow()

    private var locationJob: Job? = null
    private var combineJob: Job? = null

    init {
        // 月相/潮汐付費解鎖狀態（決定是否疊加月亮軌跡）。
        // AR 無自己的解鎖入口：只要有任一頁解鎖（或已付費）即視為解鎖。
        premiumGate.isAnyUnlocked
            .onEach {
                premiumUnlockedFlag = it
                _state.value = _state.value.copy(premiumUnlocked = it)
            }
            .launchIn(viewModelScope)
    }

    /** 磁感測器朝向校正偏移 — 從「對準太陽」校正流程算出。In-memory only。 */
    @Volatile private var azimuthOffsetDeg: Double = 0.0
    @Volatile private var pitchOffsetDeg: Double = 0.0

    /**
     * 付費解鎖旗標 — 與 [azimuthOffsetDeg] 同理採 @Volatile 直接讀取。
     * 不可只靠 combine 的 snapshot.premiumUnlocked：orientation 以 ~60Hz 觸發 recompute，
     * 其 snapshot 可能仍是舊值 (premium=false)，會把付費收集器剛寫入的 true 蓋掉，導致 AR 永遠看不到月亮。
     */
    @Volatile private var premiumUnlockedFlag: Boolean = false

    /** 鏡頭防曬遮蔽的遲滯狀態 — 避免在臨界角度反覆 bind/unbind 相機。 */
    private var cameraBlockedPrev: Boolean = false

    private companion object {
        /** 校正僅在日落前 45 分鐘內（含日落後）開放。 */
        const val CALIBRATION_WINDOW_MIN = 45.0
        /** 進入危險：太陽距「畫面邊緣」還有 12° 就遮蔽（快看到就關，不必等真的入框）。 */
        const val SUN_BLOCK_MARGIN_DEG = 12.0
        /** 解除危險：太陽需離開到畫面邊緣外 22°（遲滯帶，避免臨界反覆切換）。 */
        const val SUN_UNBLOCK_MARGIN_DEG = 22.0
    }

    /**
     * 軌跡世界座標快取 (azimuth/altitude/minutesFromSunset)。
     *
     * Why: 軌跡每點要呼叫 commons-suncalc 計算太陽位置，~20μs/點。範圍擴大到
     * now-1h ~ sunset+1h 後可能 ~120 點，每次手機轉動 (60Hz) 重算 = ~140 ms/sec CPU 燒。
     * 把世界座標快取起來，orientation 更新時只做螢幕投影 (純算術，~50ns/點)。
     *
     * 快取在 observer 或 sunsetTime 大幅變動時重建。
     */
    private var cachedTrajectory: List<TrajectoryWorldPoint> = emptyList()
    private var cachedFor: TrajectoryCacheKey? = null

    /** 月亮軌跡世界座標快取 (付費功能)。與 [cachedTrajectory] 平行。 */
    private var cachedMoonTrajectory: List<TrajectoryWorldPoint> = emptyList()
    private var cachedMoonFor: TrajectoryCacheKey? = null

    /** 由 ARScreen 在 onResume 呼叫；開啟感測器與位置監聽。 */
    fun start() {
        // 清掉上一次的錯誤訊息 (例如重新進入畫面時殘留的取消例外)
        _state.value = _state.value.copy(errorMessage = null)
        orientationProvider.start()

        if (locationProvider.hasPermission()) {
            // 用 Flow.catch (會自動跳過 CancellationException)；不要用 runCatching，
            // 否則 viewModelScope 取消時 CancellationException 會被誤當成錯誤訊息
            locationJob = locationProvider.locationUpdates()
                .onEach { loc ->
                    val obs = GeoPoint(loc.latitude, loc.longitude, loc.altitude)
                    orientationProvider.updateLocation(loc.latitude, loc.longitude, loc.altitude)
                    _state.value = _state.value.copy(observer = obs, locationAccuracyMeters = loc.accuracy)
                }
                .catch { e -> _state.value = _state.value.copy(errorMessage = e.message) }
                .launchIn(viewModelScope)
        } else {
            _state.value = _state.value.copy(errorMessage = context.getString(R.string.ar_err_location_permission))
        }

        // 結合 orientation + observer 計算螢幕投影
        combineJob = combine(
            orientationProvider.orientation,
            _state,
        ) { orientation, snapshot -> orientation to snapshot }
            .onEach { (orientation, snapshot) -> recomputeProjection(orientation, snapshot) }
            .launchIn(viewModelScope)
    }

    fun stop() {
        orientationProvider.stop()
        locationJob?.cancel()
        combineJob?.cancel()
    }

    fun setCameraFov(fov: CameraFov) {
        _state.value = _state.value.copy(cameraFov = fov)
    }

    fun setDisplayRotation(rotation: Int) {
        orientationProvider.setDisplayRotation(rotation)
    }

    /** 進入校正模式 — UI 應顯示警告 + 中央十字。 */
    fun startCalibration() {
        _state.value = _state.value.copy(calibrating = true)
    }

    fun cancelCalibration() {
        _state.value = _state.value.copy(calibrating = false)
    }

    /**
     * 使用者已對準太陽中央，按下確認 → 用當下實際太陽方位/仰角扣掉感測器報的相機朝向，
     * 得到偏移量；之後所有 cameraAz / cameraPitch 都套用此偏移。
     */
    fun confirmCalibration() {
        val snapshot = _state.value
        val observer = snapshot.observer ?: run {
            _state.value = snapshot.copy(calibrating = false, errorMessage = context.getString(R.string.ar_err_calibration_no_gps))
            return
        }
        val now = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
        val sun = sunCalc.positionAt(now, observer)

        // orientation 不包含校正偏移 (偏移只在 projection 時加)，所以直接用原始值
        val rawAz = snapshot.orientation.trueAzimuthDegrees.toDouble()
        val rawPitch = snapshot.orientation.pitchDegrees.toDouble()

        val newAzOffset = Geodesy.signedAzimuthDelta(sun.azimuthDegrees, rawAz)
        val newPitchOffset = sun.altitudeDegrees - rawPitch

        azimuthOffsetDeg = newAzOffset
        pitchOffsetDeg = newPitchOffset

        _state.value = snapshot.copy(
            calibrating = false,
            calibrationApplied = true,
            calibrationAzimuthOffsetDeg = newAzOffset,
            calibrationPitchOffsetDeg = newPitchOffset,
            calibrationTime = now,
        )
        cachedFor = null
    }

    fun resetCalibration() {
        azimuthOffsetDeg = 0.0
        pitchOffsetDeg = 0.0
        _state.value = _state.value.copy(
            calibrationApplied = false,
            calibrationAzimuthOffsetDeg = 0.0,
            calibrationPitchOffsetDeg = 0.0,
            calibrationTime = null,
        )
        cachedFor = null
    }

    private fun recomputeProjection(orientation: DeviceOrientation, snapshot: ARState) {
        val observer = snapshot.observer ?: run {
            _state.value = snapshot.copy(orientation = orientation, ready = false)
            return
        }
        val fov = snapshot.cameraFov ?: run {
            _state.value = snapshot.copy(orientation = orientation, ready = false)
            return
        }

        // 主塔幾何 (用觀察者高度)
        val geodesic = Geodesy.inverse(observer, BridgeTower.position)
        val distance = geodesic.distanceMeters.coerceAtLeast(1.0)
        val towerBearing = geodesic.initialBearingDegrees
        val towerTipAlt = Math.toDegrees(
            atan((BridgeTower.TOWER_TIP_ELEVATION_M - observer.elevationMeters) / distance)
        )
        val towerBaseAlt = Math.toDegrees(
            atan((BridgeTower.BASE_ELEVATION_M - observer.elevationMeters) / distance)
        )
        val towerMidAlt = (towerTipAlt + towerBaseAlt) / 2.0

        // 太陽當下位置
        val tz = ZoneId.of("Asia/Taipei")
        val now = ZonedDateTime.now(tz)
        val sunPos = sunCalc.positionAt(now, observer)

        // 日落時刻：今日若已過、太陽已在地平線下，自動切到「下一個日落」(明日)
        val today = LocalDate.now(tz)
        val todaySunset = sunCalc.dailyEvents(today, observer).sunset
        val sunsetTime = when {
            todaySunset == null -> sunCalc.dailyEvents(today.plusDays(1), observer).sunset
            todaySunset.isAfter(now.minusMinutes(15)) -> todaySunset
            else -> sunCalc.dailyEvents(today.plusDays(1), observer).sunset
        }

        // 投影 — 套用校正偏移
        val cameraAz = orientation.trueAzimuthDegrees.toDouble() + azimuthOffsetDeg
        val cameraPitch = orientation.pitchDegrees.toDouble() + pitchOffsetDeg

        val towerTarget = project(towerBearing, towerMidAlt, cameraAz, cameraPitch, fov)
        val sunTarget = project(sunPos.azimuthDegrees, sunPos.altitudeDegrees, cameraAz, cameraPitch, fov)

        // 軌跡：快取世界座標、只做投影；如 observer/sunset 變動才重算
        ensureTrajectoryCache(now, sunsetTime, observer)
        val trajectory = cachedTrajectory.map { wp ->
            project(wp.azimuthDegrees, wp.altitudeDegrees, cameraAz, cameraPitch, fov).copy(
                timeOffsetMinutes = ChronoUnit.MINUTES.between(now, wp.time).toInt(),
                minutesFromSunset = wp.minutesFromSunset,
            )
        }

        // ── 月亮 (付費功能；鎖定時不計算，省每幀成本) ──────────────────────
        val premium = premiumUnlockedFlag
        val moonPos = if (premium) moonCalc.moonPositionAt(now, observer) else null
        val moonTarget = moonPos?.let {
            project(it.azimuthDegrees, it.altitudeDegrees, cameraAz, cameraPitch, fov)
        } ?: ARTarget()
        ensureMoonTrajectoryCache(now, observer, premium)
        val moonTrajectory = cachedMoonTrajectory.map { wp ->
            project(wp.azimuthDegrees, wp.altitudeDegrees, cameraAz, cameraPitch, fov).copy(
                timeOffsetMinutes = ChronoUnit.MINUTES.between(now, wp.time).toInt(),
            )
        }

        // 主塔頂端與底端
        val towerTipScreen = project(towerBearing, towerTipAlt, cameraAz, cameraPitch, fov)
        val towerBaseScreen = project(towerBearing, towerBaseAlt, cameraAz, cameraPitch, fov)

        // 日落瞬間的太陽位置 (用來導引使用者轉向)
        val sunsetMarker: ARTarget?
        val sunsetAzimuth: Double?
        val sunsetAltitude: Double?
        if (sunsetTime != null) {
            val p = sunCalc.positionAt(sunsetTime, observer)
            sunsetAzimuth = p.azimuthDegrees
            sunsetAltitude = p.altitudeDegrees
            sunsetMarker = project(p.azimuthDegrees, p.altitudeDegrees, cameraAz, cameraPitch, fov)
                .copy(
                    timeOffsetMinutes = ChronoUnit.MINUTES.between(now, sunsetTime).toInt(),
                    minutesFromSunset = 0.0,
                )
        } else {
            sunsetMarker = null
            sunsetAzimuth = null
            sunsetAltitude = null
        }

        // ── 校正時段 + 防曬遮蔽 ───────────────────────────────────────────
        // 校正僅在日落前 45 分鐘內開放（此時太陽夠低、亮度可接受）。
        // 在此時段以前，若相機朝向太接近太陽（且太陽在地平線上），為保護眼睛與
        // 感光元件，遮蔽鏡頭。用遲滯帶避免臨界反覆切換。
        val minsToSunset = sunsetTime?.let {
            ChronoUnit.SECONDS.between(now, it).toDouble() / 60.0
        }
        val calibrationAllowed = minsToSunset != null && minsToSunset <= CALIBRATION_WINDOW_MIN
        val dangerApplies = minsToSunset != null &&
            minsToSunset > CALIBRATION_WINDOW_MIN &&
            sunPos.altitudeDegrees > 0.0
        // 以「畫面邊緣 + 邊距」判斷：太陽水平/垂直偏移只要逼近畫面範圍就遮蔽，
        // 不必等太陽真的進到畫面中央。遲滯：已遮蔽時要離更遠才解除。
        val margin = if (cameraBlockedPrev) SUN_UNBLOCK_MARGIN_DEG else SUN_BLOCK_MARGIN_DEG
        val nearFrameH = abs(sunTarget.horizontalOffsetDegrees) < fov.horizontalDeg / 2.0 + margin
        val nearFrameV = abs(sunTarget.verticalOffsetDegrees) < fov.verticalDeg / 2.0 + margin
        val cameraBlocked = dangerApplies && nearFrameH && nearFrameV
        cameraBlockedPrev = cameraBlocked

        _state.value = snapshot.copy(
            orientation = orientation,
            tower = towerTarget,
            towerTip = towerTipScreen,
            towerBase = towerBaseScreen,
            sun = sunTarget,
            sunTrajectory = trajectory,
            moon = moonTarget,
            moonTrajectory = if (premium) moonTrajectory else emptyList(),
            moonAzimuthDegrees = moonPos?.azimuthDegrees ?: 0.0,
            moonAltitudeDegrees = moonPos?.altitudeDegrees ?: 0.0,
            premiumUnlocked = premium,
            sunsetMarker = sunsetMarker,
            sunsetTime = sunsetTime,
            sunsetAzimuthDegrees = sunsetAzimuth,
            sunsetAltitudeDegrees = sunsetAltitude,
            distanceToTowerKm = distance / 1000.0,
            bearingToTowerDegrees = towerBearing,
            sunAzimuthDegrees = sunPos.azimuthDegrees,
            sunAltitudeDegrees = sunPos.altitudeDegrees,
            alignmentOffsetDegrees = Geodesy.signedAzimuthDelta(sunPos.azimuthDegrees, towerBearing),
            calibrationAllowed = calibrationAllowed,
            cameraBlockedBySun = cameraBlocked,
            ready = true,
        )
    }

    /**
     * 軌跡世界座標快取建立/更新。
     *
     * 範圍: **now-1h 到 sunset+1h** (使用者能看到過去 1 小時與日落後 1 小時的太陽路徑)。
     *
     * 密度 (根據距日落時刻):
     *  - ≤ 5 分: 每 30 秒一點 (極密)
     *  - 5-15 分: 每 1 分鐘一點
     *  - 其他: 每 3 分鐘一點
     *
     * 快取以 (observerHash, sunsetEpochMin, nowEpochMin/5) 為 key — `now` 每 5 分鐘
     * 改變才重建，避免每秒重算。
     */
    private fun ensureTrajectoryCache(
        now: ZonedDateTime,
        sunsetTime: ZonedDateTime?,
        observer: GeoPoint,
    ) {
        if (sunsetTime == null) {
            cachedTrajectory = emptyList()
            cachedFor = null
            return
        }
        val key = TrajectoryCacheKey(
            observerLatRound = (observer.latitude * 10000).toInt(),
            observerLonRound = (observer.longitude * 10000).toInt(),
            sunsetEpochMin = sunsetTime.toEpochSecond() / 60,
            nowEpochMin5 = now.toEpochSecond() / 60 / 5,
        )
        if (key == cachedFor) return

        val rangeStart = now.minusHours(1)
        val rangeEnd = sunsetTime.plusHours(1)

        val points = mutableListOf<TrajectoryWorldPoint>()
        var t = rangeStart
        while (!t.isAfter(rangeEnd)) {
            val absFromSunset = abs(ChronoUnit.SECONDS.between(t, sunsetTime).toDouble() / 60.0)
            val pos = sunCalc.positionAt(t, observer)
            points.add(
                TrajectoryWorldPoint(
                    time = t,
                    azimuthDegrees = pos.azimuthDegrees,
                    altitudeDegrees = pos.altitudeDegrees,
                    minutesFromSunset = absFromSunset,
                )
            )
            val stepSeconds = when {
                absFromSunset < 5 -> 30L
                absFromSunset < 15 -> 60L
                else -> 180L
            }
            t = t.plusSeconds(stepSeconds)
        }
        cachedTrajectory = points
        cachedFor = key
    }

    /**
     * 月亮軌跡世界座標快取 (付費功能)。範圍 now-1h ~ now+5h，每 4 分鐘一點 (前瞻月亮路徑)。
     * 鎖定時清空。快取以 (observer, now/5min) 為 key（sunsetEpochMin 欄位對月亮不適用，填 0）。
     */
    private fun ensureMoonTrajectoryCache(
        now: ZonedDateTime,
        observer: GeoPoint,
        premium: Boolean,
    ) {
        if (!premium) {
            cachedMoonTrajectory = emptyList()
            cachedMoonFor = null
            return
        }
        val key = TrajectoryCacheKey(
            observerLatRound = (observer.latitude * 10000).toInt(),
            observerLonRound = (observer.longitude * 10000).toInt(),
            sunsetEpochMin = 0,
            nowEpochMin5 = now.toEpochSecond() / 60 / 5,
        )
        if (key == cachedMoonFor) return

        val points = mutableListOf<TrajectoryWorldPoint>()
        var t = now.minusHours(1)
        val end = now.plusHours(5)
        while (!t.isAfter(end)) {
            val pos = moonCalc.moonPositionAt(t, observer)
            points.add(
                TrajectoryWorldPoint(
                    time = t,
                    azimuthDegrees = pos.azimuthDegrees,
                    altitudeDegrees = pos.altitudeDegrees,
                    minutesFromSunset = Double.NaN,
                )
            )
            t = t.plusMinutes(4)
        }
        cachedMoonTrajectory = points
        cachedMoonFor = key
    }

    private fun project(
        targetAzimuth: Double,
        targetAltitude: Double,
        cameraAzimuth: Double,
        cameraPitch: Double,
        fov: CameraFov,
    ): ARTarget {
        val hOffset = Geodesy.signedAzimuthDelta(targetAzimuth, cameraAzimuth)
        val vOffset = targetAltitude - cameraPitch
        val xFrac = 0.5 + hOffset / fov.horizontalDeg
        val yFrac = 0.5 - vOffset / fov.verticalDeg
        val inFrameH = hOffset in -(fov.horizontalDeg / 2)..(fov.horizontalDeg / 2)
        val inFrameV = vOffset in -(fov.verticalDeg / 2)..(fov.verticalDeg / 2)
        return ARTarget(
            xFrac = xFrac,
            yFrac = yFrac,
            inFrame = inFrameH && inFrameV,
            offFrameLeft = hOffset < -fov.horizontalDeg / 2,
            offFrameRight = hOffset > fov.horizontalDeg / 2,
            offFrameTop = vOffset > fov.verticalDeg / 2,
            offFrameBottom = vOffset < -fov.verticalDeg / 2,
            horizontalOffsetDegrees = hOffset,
            verticalOffsetDegrees = vOffset,
        )
    }
}

data class ARState(
    val observer: GeoPoint? = null,
    val locationAccuracyMeters: Float? = null,
    val orientation: DeviceOrientation = DeviceOrientation(),
    // 用預設 FOV 開場，CameraPreview 計算完會覆寫；確保 GPS 一鎖到就能繪製 overlay
    val cameraFov: CameraFov? = CameraFov.DEFAULT,
    val tower: ARTarget = ARTarget(),
    val towerTip: ARTarget = ARTarget(),
    val towerBase: ARTarget = ARTarget(),
    val sun: ARTarget = ARTarget(),
    val sunTrajectory: List<ARTarget> = emptyList(),
    /** 月亮當下位置 (付費功能)。 */
    val moon: ARTarget = ARTarget(),
    /** 月亮軌跡 (付費功能；鎖定時為空)。 */
    val moonTrajectory: List<ARTarget> = emptyList(),
    val moonAzimuthDegrees: Double = 0.0,
    val moonAltitudeDegrees: Double = 0.0,
    /** 月相/潮汐付費功能是否解鎖。 */
    val premiumUnlocked: Boolean = false,
    val sunsetMarker: ARTarget? = null,
    val sunsetTime: ZonedDateTime? = null,
    val sunsetAzimuthDegrees: Double? = null,
    val sunsetAltitudeDegrees: Double? = null,
    val distanceToTowerKm: Double = 0.0,
    val bearingToTowerDegrees: Double = 0.0,
    val sunAzimuthDegrees: Double = 0.0,
    val sunAltitudeDegrees: Double = 0.0,
    val alignmentOffsetDegrees: Double = 0.0,
    val ready: Boolean = false,
    val errorMessage: String? = null,
    /** 是否在校正開放時段（日落前 45 分鐘內）。 */
    val calibrationAllowed: Boolean = false,
    /** 鏡頭因太接近太陽（且非日落前 45 分鐘時段）被遮蔽保護。 */
    val cameraBlockedBySun: Boolean = false,
    val calibrating: Boolean = false,
    val calibrationApplied: Boolean = false,
    val calibrationAzimuthOffsetDeg: Double = 0.0,
    val calibrationPitchOffsetDeg: Double = 0.0,
    val calibrationTime: ZonedDateTime? = null,
)

data class ARTarget(
    val xFrac: Double = 0.5,
    val yFrac: Double = 0.5,
    val inFrame: Boolean = false,
    val offFrameLeft: Boolean = false,
    val offFrameRight: Boolean = false,
    val offFrameTop: Boolean = false,
    val offFrameBottom: Boolean = false,
    val horizontalOffsetDegrees: Double = 0.0,
    val verticalOffsetDegrees: Double = 0.0,
    val timeOffsetMinutes: Int = 0,
    /** 距日落時刻的絕對分鐘數；NaN 代表沒有 sunset 資料。距離越小，繪製時點越大越亮。 */
    val minutesFromSunset: Double = Double.NaN,
)

/** 軌跡點的世界座標快取單元 — 與 orientation 無關，只在 observer/sunset 改變時重算。 */
private data class TrajectoryWorldPoint(
    val time: ZonedDateTime,
    val azimuthDegrees: Double,
    val altitudeDegrees: Double,
    val minutesFromSunset: Double,
)

/** 軌跡快取的 key — 觀察者位置 (取 4 位小數約 ~10m 精度) + 日落分鐘 + 當下分鐘/5 */
private data class TrajectoryCacheKey(
    val observerLatRound: Int,
    val observerLonRound: Int,
    val sunsetEpochMin: Long,
    val nowEpochMin5: Long,
)
