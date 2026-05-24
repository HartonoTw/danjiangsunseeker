package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import studio.freestyle.labs.danjiangsunseeker.data.astro.SunCalcDataSource
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
 * 「逆向推算 / 黃金拍攝帶」核心 use case。
 *
 * 流程:
 *  1. 給定日期 → 取得當日日落瞬間的太陽方位 A (從主塔觀測，海平面 elevation)
 *  2. 從主塔朝反方位 (A) 畫測地射線 (註：太陽在主塔西邊，使用者必須位於主塔東邊看西邊
 *     才能看到太陽落於主塔後方，所以射線方位 = 太陽方位的反方向不一定 ── 我們其實要從
 *     主塔朝太陽方位的「反向」延伸到觀察者所在處。實際上射線從主塔朝 (A) 方向走，因為
 *     地平面上「太陽在主塔上方」的觀察者位於太陽方位的反向 — 從主塔看出去的方向是太陽方位 A，
 *     但觀察者站在主塔朝 A 的反方向 (A + 180°) 才能讓視線剛好把主塔擺在太陽前面)
 *  3. 沿射線等距採樣 N 個點，每點計算主塔角寬作為可容忍方位誤差
 *
 * 結論: 觀察者要拍「夕陽穿塔」必須位於 **bearingFromTower = A + 180° (mod 360°)** 的方向。
 */
class ComputeGoldenLineUseCase @Inject constructor(
    private val sunCalc: SunCalcDataSource,
) {
    /**
     * @param date 目標日期
     * @param maxRangeKm 沿射線延伸的最遠距離 (預設 12 km 已涵蓋大屯山頂)
     * @param sampleStepMeters 採樣間距 (預設 100 m)
     */
    operator fun invoke(
        date: LocalDate,
        maxRangeKm: Double = 12.0,
        sampleStepMeters: Double = 100.0,
        target: TowerTarget = TowerTarget.LowerY,
    ): GoldenLine? {
        val events = sunCalc.dailyEvents(date, BridgeTower.position)
        val sunsetAzimuth = events.sunsetAzimuthDegrees ?: return null
        val sunsetTime = events.sunset ?: return null
        val maxDistM = maxRangeKm * 1000.0
        val numSamples = (maxDistM / sampleStepMeters).toInt()

        val samples = (1..numSamples).mapNotNull { i ->
            val distance = sampleStepMeters * i
            val targetAltitude = Math.toDegrees(
                atan((target.elevationMeters - GOLDEN_LINE_OBSERVER_ELEVATION_M) / distance),
            )
            val eventTime = findDescendingAltitudeTime(
                targetAltitudeDegrees = targetAltitude,
                startTime = sunsetTime.minusMinutes(180),
                endTime = sunsetTime.plusMinutes(30),
            ) ?: return@mapNotNull null
            val sun = sunCalc.positionAt(eventTime, BridgeTower.position).azimuthDegrees
            val bearingFromTower = (sun + 180.0) % 360.0
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

    private fun findDescendingAltitudeTime(
        targetAltitudeDegrees: Double,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
    ): ZonedDateTime? {
        var lo = startTime
        var hi = endTime
        val loAlt = sunCalc.positionAt(lo, BridgeTower.position).altitudeDegrees
        val hiAlt = sunCalc.positionAt(hi, BridgeTower.position).altitudeDegrees
        if (targetAltitudeDegrees !in hiAlt..loAlt) return null

        repeat(22) {
            val mid = lo.plusNanos(Duration.between(lo, hi).toNanos() / 2)
            val midAlt = sunCalc.positionAt(mid, BridgeTower.position).altitudeDegrees
            if (midAlt > targetAltitudeDegrees) lo = mid else hi = mid
        }
        return hi
    }

    private data class GoldenLineSample(
        val point: GoldenLinePoint,
        val bearingFromTowerDegrees: Double,
        val eventTime: ZonedDateTime,
    )

    private companion object {
        /**
         * Map page 的黃金線沒有 DEM，所以先以海平面觀測者估算。
         * 這讓塔基 30m / 塔頂 200m 都會依距離轉成目標仰角，而不是把塔基當日落地平線。
         */
        private const val GOLDEN_LINE_OBSERVER_ELEVATION_M = 0.0
    }
}
