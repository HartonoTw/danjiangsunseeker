package studio.freestyle.labs.danjiangsunseeker.data.astro

import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.LunarPhase
import studio.freestyle.labs.danjiangsunseeker.domain.model.MoonInfo
import studio.freestyle.labs.danjiangsunseeker.domain.model.MoonPosition as DomainMoonPosition
import studio.freestyle.labs.danjiangsunseeker.domain.physics.AtmosphericRefraction
import org.shredzone.commons.suncalc.MoonIllumination
import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.MoonTimes
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 包裝 commons-suncalc (v3.11) 的月亮計算 — 與 [SunCalcDataSource] 平行、完全離線。
 *
 * 注意:
 *  - 與太陽相同，v3.x 的 [MoonPosition.getAltitude] 為幾何仰角 (已含地平視差，但**不含**大氣折射)。
 *    本類別在 [moonPositionAt] 手動套用 [AtmosphericRefraction.apparentFromTrue] 產生視仰角。
 *  - [MoonIllumination] 不需觀測者位置 (亮面比例近似為地心量)，故只給時間。
 *  - 盈虧 (waxing) 不依賴函式庫的相位角正負慣例，改用「6 小時後亮面是否增加」判斷，較穩健。
 */
@Singleton
class MoonCalcDataSource @Inject constructor() {

    /** 計算某瞬間從觀察者看月亮的位置 (含手動套用的大氣折射修正)。 */
    fun moonPositionAt(time: ZonedDateTime, observer: GeoPoint): DomainMoonPosition {
        val result = MoonPosition.compute()
            .on(time)
            .at(observer.latitude, observer.longitude)
            .elevation(observer.elevationMeters)
            .execute()

        val trueAlt = result.altitude // v3.x: 幾何仰角 (含視差、未含折射)
        val apparentAlt = AtmosphericRefraction.apparentFromTrue(trueAlt)
        return DomainMoonPosition(
            time = time,
            azimuthDegrees = result.azimuth,
            altitudeDegrees = apparentAlt,
            trueAltitudeDegrees = trueAlt,
            distanceKm = result.distance,
        )
    }

    /**
     * 當日亮面比例與盈虧 (與觀測者位置無關)。
     * @return Pair(fractionLit 0..1, waxing)
     */
    fun illumination(date: LocalDate): Pair<Double, Boolean> {
        val noon = date.atTime(12, 0).atZone(TAIPEI)
        val fractionNow = MoonIllumination.compute().on(noon).execute().fraction
        val fractionLater = MoonIllumination.compute().on(noon.plusHours(6)).execute().fraction
        return fractionNow to (fractionLater >= fractionNow)
    }

    /** 當日月出 / 月落時間 (極端緯度可能整天在地平上/下，此時對應值為 null)。 */
    fun moonTimes(date: LocalDate, observer: GeoPoint): MoonRiseSet {
        val times = MoonTimes.compute()
            .on(date.atStartOfDay(TAIPEI))
            .at(observer.latitude, observer.longitude)
            .elevation(observer.elevationMeters)
            .oneDay()
            .execute()
        return MoonRiseSet(rise = times.rise, set = times.set)
    }

    /** 彙整當日月亮資訊：月出月落、亮面比例、盈虧、相位、月落方位。 */
    fun dailyMoon(date: LocalDate, observer: GeoPoint): MoonInfo {
        val rs = moonTimes(date, observer)

        // 以當地正午為相位代表時間 (一天內亮面比例變化 < 0.04，取正午足以代表當日相位)。
        val noon = date.atTime(12, 0).atZone(TAIPEI)
        val fractionNow = MoonIllumination.compute().on(noon).execute().fraction
        val fractionLater = MoonIllumination.compute().on(noon.plusHours(6)).execute().fraction
        val waxing = fractionLater >= fractionNow

        val azimuthAtSet = rs.set?.let { moonPositionAt(it, observer).azimuthDegrees }

        return MoonInfo(
            date = date,
            observer = observer,
            rise = rs.rise,
            set = rs.set,
            fractionLit = fractionNow,
            waxing = waxing,
            phase = classify(fractionNow, waxing),
            azimuthAtSet = azimuthAtSet,
        )
    }

    /** 由亮面比例 + 盈虧判定八個主要月相（供 UI 顯示月象名稱）。 */
    fun classify(fraction: Double, waxing: Boolean): LunarPhase = when {
        fraction < 0.04 -> LunarPhase.NEW
        fraction > 0.96 -> LunarPhase.FULL
        fraction in 0.46..0.54 -> if (waxing) LunarPhase.FIRST_QUARTER else LunarPhase.LAST_QUARTER
        fraction < 0.46 -> if (waxing) LunarPhase.WAXING_CRESCENT else LunarPhase.WANING_CRESCENT
        else -> if (waxing) LunarPhase.WAXING_GIBBOUS else LunarPhase.WANING_GIBBOUS
    }

    private companion object {
        val TAIPEI: ZoneId = ZoneId.of("Asia/Taipei")
    }
}

/** [MoonCalcDataSource.moonTimes] 回傳值。 */
data class MoonRiseSet(
    val rise: ZonedDateTime?,
    val set: ZonedDateTime?,
)
