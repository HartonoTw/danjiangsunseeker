package studio.freestyle.labs.danjiangsunseeker.data.astro

import studio.freestyle.labs.danjiangsunseeker.domain.model.TideExtreme
import studio.freestyle.labs.danjiangsunseeker.domain.model.TideInfo
import studio.freestyle.labs.danjiangsunseeker.domain.model.TideKind
import studio.freestyle.labs.danjiangsunseeker.domain.physics.TideHarmonics
import studio.freestyle.labs.danjiangsunseeker.domain.physics.TideHarmonics.Station
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 潮汐資料來源 — 以 [TideHarmonics] 調和分析離線推算淡水站潮汐。
 *
 * 預設測站為 [TideStations.TAMSUI]；高低潮時刻為攝影規劃 (前景、倒影、退潮地貌) 之依據。
 */
@Singleton
class TideDataSource @Inject constructor() {

    /**
     * 推算指定日期的潮汐資訊。
     *
     * @param now 若非 null 且日期等於 [date]，會附上「現在」潮位與漲退方向
     */
    fun tidesFor(
        date: LocalDate,
        station: Station = TideStations.TAMSUI,
        now: ZonedDateTime? = null,
    ): TideInfo {
        val extremes = TideHarmonics.dailyExtremes(station, date, TAIPEI).map {
            TideExtreme(
                time = it.time,
                heightMeters = it.heightMeters,
                kind = if (it.high) TideKind.HIGH else TideKind.LOW,
            )
        }

        val currentHeight: Double?
        val rising: Boolean?
        if (now != null && now.withZoneSameInstant(TAIPEI).toLocalDate() == date) {
            currentHeight = TideHarmonics.heightMeters(station, now)
            rising = TideHarmonics.isRising(station, now)
        } else {
            currentHeight = null
            rising = null
        }

        return TideInfo(
            date = date,
            extremes = extremes,
            currentHeightMeters = currentHeight,
            rising = rising,
        )
    }

    private companion object {
        val TAIPEI: ZoneId = ZoneId.of("Asia/Taipei")
    }
}
