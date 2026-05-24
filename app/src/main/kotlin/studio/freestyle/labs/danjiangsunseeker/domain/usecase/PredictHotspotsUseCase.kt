package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import studio.freestyle.labs.danjiangsunseeker.data.astro.SunCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.DailySunEvents
import studio.freestyle.labs.danjiangsunseeker.domain.model.DefaultHotspots
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import studio.freestyle.labs.danjiangsunseeker.domain.model.SunTrailPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max

/**
 * 為每個熱點計算今日日落事件 + 與主塔的方位關係。
 *
 * 主要產出：
 *  - 日落時間、方位、仰角
 *  - 觀測者 → 主塔的方位 (initial bearing)
 *  - 太陽方位與主塔方位的偏差 (signed)
 *  - 主塔在此距離下的「角寬」(角直徑) — 可作為穿塔容差
 */
class PredictHotspotsUseCase @Inject constructor(
    private val sunCalc: SunCalcDataSource,
    private val targetSunResolver: TowerTargetSunResolver,
) {
    operator fun invoke(
        date: LocalDate,
        hotspots: List<Hotspot> = DefaultHotspots.ALL,
        target: TowerTarget = TowerTarget.UpperY,
    ): List<HotspotPrediction> =
        hotspots.map { hotspot ->
            val geodesic = Geodesy.inverse(hotspot.position, BridgeTower.position)

            // 距主塔 > 25 km 視為「太遠」— 不執行天文計算 (省 CPU + 提示使用者選太遠)
            if (geodesic.distanceMeters > MAX_DISTANCE_METERS) {
                return@map HotspotPrediction(
                    hotspot = hotspot,
                    events = sunCalc.dailyEvents(date, hotspot.position).copy(
                        sunsetAzimuthDegrees = null,
                    ),
                    distanceToTowerMeters = geodesic.distanceMeters,
                    bearingToTowerDegrees = geodesic.initialBearingDegrees,
                    alignmentOffsetDegrees = null,
                    towerAngularWidthDegrees = 0.0,
                    classification = AlignmentClass.TOO_FAR,
                    lastHourSunTrail = emptyList(),
                    targetTime = null,
                    targetAzimuthDegrees = null,
                    towerTarget = target,
                )
            }

            val events = sunCalc.dailyEvents(date, hotspot.position)
            val targetEvent = targetSunResolver.resolve(date, hotspot.position, target)
            val sunAzimuth = targetEvent.azimuthDegrees
            val alignmentOffset = sunAzimuth?.let {
                Geodesy.signedAzimuthDelta(it, geodesic.initialBearingDegrees)
            }
            val angularWidth = towerAngularWidthDegrees(geodesic.distanceMeters)
            val classification = when {
                alignmentOffset == null -> AlignmentClass.UNKNOWN
                abs(alignmentOffset) <= angularWidth / 2 -> AlignmentClass.PERFECT
                abs(alignmentOffset) <= 2.0 -> AlignmentClass.NEAR
                else -> AlignmentClass.FAR
            }
            // 日落前 60 分鐘到日落瞬間，每 5 分鐘一個取樣點（共 13 點）。用於 UI 縮圖。
            val targetTime = targetEvent.time ?: events.sunset
            val trail = if (targetTime != null) {
                (0..TRAIL_MINUTES step TRAIL_INTERVAL_MIN).map { minutesBefore ->
                    val t = targetTime.minusMinutes(minutesBefore.toLong())
                    val pos = sunCalc.positionAt(t, hotspot.position)
                    SunTrailPoint(
                        minutesBeforeSunset = minutesBefore,
                        azimuthDegrees = pos.azimuthDegrees,
                        altitudeDegrees = pos.altitudeDegrees,
                    )
                }.reversed() // 從最早 (60min before) 到最晚 (sunset)
            } else emptyList()
            HotspotPrediction(
                hotspot = hotspot,
                events = events,
                distanceToTowerMeters = geodesic.distanceMeters,
                bearingToTowerDegrees = geodesic.initialBearingDegrees,
                alignmentOffsetDegrees = alignmentOffset,
                towerAngularWidthDegrees = angularWidth,
                classification = classification,
                lastHourSunTrail = trail,
                targetTime = targetTime,
                targetAzimuthDegrees = sunAzimuth,
                towerTarget = target,
            )
        }

    companion object {
        const val MAX_DISTANCE_METERS = 25_000.0
        /** 縮圖太陽軌跡的取樣總長度（分鐘）。 */
        const val TRAIL_MINUTES = 60
        /** 取樣間隔（分鐘）。60 / 5 + 1 = 13 個點。 */
        const val TRAIL_INTERVAL_MIN = 5
    }

    private fun towerAngularWidthDegrees(distanceMeters: Double): Double {
        val safeDist = max(distanceMeters, 1.0)
        // 2 * atan( (寬/2) / 距離 )
        return 2.0 * Math.toDegrees(atan((BridgeTower.TOWER_WIDTH_M / 2.0) / safeDist))
    }
}

data class HotspotPrediction(
    val hotspot: Hotspot,
    val events: DailySunEvents,
    val distanceToTowerMeters: Double,
    val bearingToTowerDegrees: Double,
    val alignmentOffsetDegrees: Double?,
    val towerAngularWidthDegrees: Double,
    val classification: AlignmentClass,
    /** 日落前 60 分鐘到日落瞬間的太陽軌跡取樣點。TOO_FAR 或無日落時為空。 */
    val lastHourSunTrail: List<SunTrailPoint> = emptyList(),
    val targetTime: ZonedDateTime? = null,
    val targetAzimuthDegrees: Double? = null,
    val towerTarget: TowerTarget = TowerTarget.UpperY,
)

enum class AlignmentClass { PERFECT, NEAR, FAR, UNKNOWN, TOO_FAR }
