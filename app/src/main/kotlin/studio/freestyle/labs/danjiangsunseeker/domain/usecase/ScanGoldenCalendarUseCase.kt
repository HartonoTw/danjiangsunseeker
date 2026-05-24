package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import studio.freestyle.labs.danjiangsunseeker.data.astro.SunCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.DefaultHotspots
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.abs

/**
 * 掃描未來 N 天，找出「太陽日落方位與主塔對齊度 ≤ threshold」的日期。
 *
 * 由於台灣緯度範圍下日落方位每年大約 245°-295° 之間擺盪 (50° span)，
 * 對某熱點的「主塔方位」是固定值，只有 2 個窗口會接近完美對齊
 * (春夏的方位上升期 + 夏秋的下降期，環繞夏至附近) — 或者根本沒有 (若主塔方位
 * 超出 245°-295° 範圍，如八里渡船頭朝主塔 307°)。
 *
 * 演算法是 O(N) brute force：N=365 + hotspots=11 = ~4000 次 SunCalc 計算，
 * 在現代手機約 200-300ms，可接受。需要更高效率時可改為二分搜尋過零點。
 */
class ScanGoldenCalendarUseCase @Inject constructor(
    private val sunCalc: SunCalcDataSource,
) {

    /**
     * @param fromDate 起始日期
     * @param days 掃描天數 (預設 365)
     * @param maxOffsetDegrees 對齊容差 (預設 2°，可放寬到 5° 看到更多日子)
     * @param hotspots 要掃描的熱點 (預設全部)
     */
    operator fun invoke(
        fromDate: LocalDate,
        days: Int = 365,
        maxOffsetDegrees: Double = 2.0,
        hotspots: List<Hotspot> = DefaultHotspots.ALL,
    ): List<GoldenDate> {
        val result = mutableListOf<GoldenDate>()
        val hotspotBearings = hotspots.associateWith { hotspot ->
            Geodesy.inverse(hotspot.position, BridgeTower.position).initialBearingDegrees
        }

        for (d in 0 until days) {
            val date = fromDate.plusDays(d.toLong())
            for (hotspot in hotspots) {
                val events = sunCalc.dailyEvents(date, hotspot.position)
                val sunsetAz = events.sunsetAzimuthDegrees ?: continue
                val towerBearing = hotspotBearings.getValue(hotspot)
                val offset = Geodesy.signedAzimuthDelta(sunsetAz, towerBearing)
                if (abs(offset) <= maxOffsetDegrees) {
                    result += GoldenDate(
                        date = date,
                        hotspot = hotspot,
                        sunsetTime = events.sunset,
                        sunsetAzimuthDegrees = sunsetAz,
                        towerBearingDegrees = towerBearing,
                        alignmentOffsetDegrees = offset,
                    )
                }
            }
        }
        return result.sortedWith(compareBy({ it.date }, { abs(it.alignmentOffsetDegrees) }))
    }
}

data class GoldenDate(
    val date: LocalDate,
    val hotspot: Hotspot,
    val sunsetTime: ZonedDateTime?,
    val sunsetAzimuthDegrees: Double,
    val towerBearingDegrees: Double,
    val alignmentOffsetDegrees: Double,
)
