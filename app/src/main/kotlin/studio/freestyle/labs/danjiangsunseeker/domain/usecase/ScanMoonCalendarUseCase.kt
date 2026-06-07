package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import studio.freestyle.labs.danjiangsunseeker.data.astro.MoonCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.data.astro.TideDataSource
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.DefaultHotspots
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import studio.freestyle.labs.danjiangsunseeker.domain.model.TideInfo
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.abs

/**
 * 「月亮穿塔」日曆掃描 (付費功能) — [ScanGoldenCalendarUseCase] 的月亮版。
 *
 * 對每個 (日期 × 熱點)，以**月落瞬間**的月亮方位與「觀察者→主塔」方位比較，
 * 偏差在容差內即為「月亮對齊日」。回傳 [GoldenDate] (isMoon = true) 以重用日曆 UI。
 *
 * Note: v1 以月落 (地平方位) 為基準，未細分塔頂/塔基目標仰角；日後可比照
 *   [TowerTargetSunResolver] 加一個月亮版解析器以對應不同目標高度。
 */
class ScanMoonCalendarUseCase @Inject constructor(
    private val moonCalc: MoonCalcDataSource,
    private val tideDataSource: TideDataSource,
) {
    operator fun invoke(
        fromDate: LocalDate,
        days: Int = 365,
        maxOffsetDegrees: Double = 2.0,
        hotspots: List<Hotspot> = DefaultHotspots.ALL,
        target: TowerTarget = TowerTarget.UpperY,
        includeTide: Boolean = true,
    ): List<GoldenDate> {
        val result = mutableListOf<GoldenDate>()
        val hotspotBearings = hotspots.associateWith { hotspot ->
            Geodesy.inverse(hotspot.position, BridgeTower.position).initialBearingDegrees
        }
        val tideByDate = HashMap<LocalDate, TideInfo>()
        // 月相亮面與觀測者無關，按日期快取 (僅命中日計算)
        val illumByDate = HashMap<LocalDate, Pair<Double, Boolean>>()

        for (d in 0 until days) {
            val date = fromDate.plusDays(d.toLong())
            for (hotspot in hotspots) {
                val moonset = moonCalc.moonTimes(date, hotspot.position).set ?: continue
                // 排除月落落在白天時段 (10:00–16:00)：天空太亮，月亮幾乎看不見
                val setLocal = moonset.toLocalTime()
                if (setLocal >= DAYTIME_EXCLUDE_START && setLocal < DAYTIME_EXCLUDE_END) continue
                val moonAz = moonCalc.moonPositionAt(moonset, hotspot.position).azimuthDegrees
                val towerBearing = hotspotBearings.getValue(hotspot)
                val offset = Geodesy.signedAzimuthDelta(moonAz, towerBearing)
                if (abs(offset) <= maxOffsetDegrees) {
                    val (frac, waxing) = illumByDate.getOrPut(date) { moonCalc.illumination(date) }
                    // 排除月亮全黑 (近新月)：亮面過低時根本拍不到
                    if (frac < MIN_LIT_FRACTION) continue
                    val tide = if (includeTide) tideByDate.getOrPut(date) { tideDataSource.tidesFor(date) } else null
                    result += GoldenDate(
                        date = date,
                        hotspot = hotspot,
                        sunsetTime = moonset,
                        sunsetAzimuthDegrees = moonAz,
                        towerBearingDegrees = towerBearing,
                        alignmentOffsetDegrees = offset,
                        towerTarget = target,
                        isMoon = true,
                        moonFractionLit = frac,
                        moonWaxing = waxing,
                        tideInfo = tide,
                    )
                }
            }
        }
        return result.sortedWith(compareBy({ it.date }, { abs(it.alignmentOffsetDegrees) }))
    }

    private companion object {
        /** 月落落在此白天時段內排除 (天空太亮，月亮幾乎不可見)：10:00–16:00。 */
        val DAYTIME_EXCLUDE_START: LocalTime = LocalTime.of(10, 0)
        val DAYTIME_EXCLUDE_END: LocalTime = LocalTime.of(16, 0)
        /** 亮面比例低於此值視為「月亮全黑」(近新月) 而排除。 */
        const val MIN_LIT_FRACTION = 0.05
    }
}
