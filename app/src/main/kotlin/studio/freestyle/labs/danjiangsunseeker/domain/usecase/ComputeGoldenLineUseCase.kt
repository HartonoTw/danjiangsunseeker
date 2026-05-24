package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import studio.freestyle.labs.danjiangsunseeker.data.astro.SunCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.GoldenLine
import studio.freestyle.labs.danjiangsunseeker.domain.model.GoldenLinePoint
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import java.time.LocalDate
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
    ): GoldenLine? {
        val events = sunCalc.dailyEvents(date, BridgeTower.position)
        val sunsetAzimuth = events.sunsetAzimuthDegrees ?: return null
        val sunsetTime = events.sunset ?: return null

        // 觀察者位於主塔朝 (sunsetAzimuth + 180°) 的方向上
        val bearingFromTower = (sunsetAzimuth + 180.0) % 360.0
        val maxDistM = maxRangeKm * 1000.0
        val numSamples = (maxDistM / sampleStepMeters).toInt()

        val points = (1..numSamples).map { i ->
            val dist = sampleStepMeters * i
            val pt = Geodesy.direct(BridgeTower.position, bearingFromTower, dist)
            GoldenLinePoint(
                point = pt,
                distanceFromTowerMeters = dist,
                towerAngularWidthDegrees =
                    2.0 * Math.toDegrees(atan((BridgeTower.TOWER_WIDTH_M / 2.0) / dist)),
            )
        }

        return GoldenLine(
            fromTower = BridgeTower.position,
            bearingFromTowerDegrees = bearingFromTower,
            sampledPoints = points,
            maxRangeKm = maxRangeKm,
            eventTime = sunsetTime,
        )
    }
}
