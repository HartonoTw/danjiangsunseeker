package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import studio.freestyle.labs.danjiangsunseeker.data.astro.MoonCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.GoldenLine
import studio.freestyle.labs.danjiangsunseeker.domain.model.GoldenLinePoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.atan

/**
 * 「月亮黃金拍攝帶」— [ComputeGoldenLineUseCase] 的月亮版 (付費功能)。
 *
 * 與太陽版邏輯相同，但以**月出 (moonrise)** 為錨點：月亮在月出後上升穿越各「目標仰角」的瞬間，
 * 取其方位 A，觀察者位於 bearingFromTower = A + 180° 方向，即可拍到「月亮升起穿塔」(大月亮從主塔後升起)。
 *
 * 當日無月出 (moonrise == null，極端緯度或月亮整天在地平上/下) 時回傳 null。
 */
class ComputeMoonGoldenLineUseCase @Inject constructor(
    private val moonCalc: MoonCalcDataSource,
) {
    operator fun invoke(
        date: LocalDate,
        maxRangeKm: Double = 12.0,
        sampleStepMeters: Double = 100.0,
        target: TowerTarget = TowerTarget.LowerY,
    ): GoldenLine? {
        val moonrise = moonCalc.moonTimes(date, BridgeTower.position).rise ?: return null
        val maxDistM = maxRangeKm * 1000.0
        val numSamples = (maxDistM / sampleStepMeters).toInt()

        val samples = (1..numSamples).mapNotNull { i ->
            val distance = sampleStepMeters * i
            val targetAltitude = Math.toDegrees(
                atan((target.elevationMeters - GOLDEN_LINE_OBSERVER_ELEVATION_M) / distance),
            )
            // 月出後月亮「上升」穿越目標仰角 → 找上升交點
            val eventTime = findAscendingAltitudeTime(
                targetAltitudeDegrees = targetAltitude,
                startTime = moonrise.minusMinutes(30),
                endTime = moonrise.plusMinutes(180),
            ) ?: return@mapNotNull null
            val moonAz = moonCalc.moonPositionAt(eventTime, BridgeTower.position).azimuthDegrees
            val bearingFromTower = (moonAz + 180.0) % 360.0
            val point = Geodesy.direct(BridgeTower.position, bearingFromTower, distance)
            GoldenLineSample(
                point = GoldenLinePoint(
                    point = point,
                    distanceFromTowerMeters = distance,
                    towerAngularWidthDegrees =
                        2.0 * Math.toDegrees(atan((BridgeTower.TOWER_WIDTH_M / 2.0) / distance)),
                ),
                bearingFromTowerDegrees = bearingFromTower,
                eventTime = eventTime,
            )
        }
        if (samples.isEmpty()) return null

        val representative = samples[samples.lastIndex / 2]
        return GoldenLine(
            fromTower = BridgeTower.position,
            bearingFromTowerDegrees = representative.bearingFromTowerDegrees,
            sampledPoints = samples.map { it.point },
            maxRangeKm = maxRangeKm,
            eventTime = representative.eventTime,
            target = target,
        )
    }

    /** 在 [startTime, endTime] 內找月亮「上升」穿越 [targetAltitudeDegrees] 的時刻 (alt 隨時間遞增)。 */
    private fun findAscendingAltitudeTime(
        targetAltitudeDegrees: Double,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
    ): ZonedDateTime? {
        var lo = startTime
        var hi = endTime
        val loAlt = moonCalc.moonPositionAt(lo, BridgeTower.position).altitudeDegrees
        val hiAlt = moonCalc.moonPositionAt(hi, BridgeTower.position).altitudeDegrees
        // 上升段：起點仰角低、終點仰角高；目標需落在兩者之間
        if (targetAltitudeDegrees !in loAlt..hiAlt) return null

        repeat(22) {
            val mid = lo.plusNanos(Duration.between(lo, hi).toNanos() / 2)
            val midAlt = moonCalc.moonPositionAt(mid, BridgeTower.position).altitudeDegrees
            if (midAlt < targetAltitudeDegrees) lo = mid else hi = mid
        }
        return hi
    }

    private data class GoldenLineSample(
        val point: GoldenLinePoint,
        val bearingFromTowerDegrees: Double,
        val eventTime: ZonedDateTime,
    )

    private companion object {
        private const val GOLDEN_LINE_OBSERVER_ELEVATION_M = 0.0
    }
}
