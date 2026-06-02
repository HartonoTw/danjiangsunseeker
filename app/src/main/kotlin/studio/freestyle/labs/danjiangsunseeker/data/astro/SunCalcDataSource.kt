package studio.freestyle.labs.danjiangsunseeker.data.astro

import studio.freestyle.labs.danjiangsunseeker.domain.model.DailySunEvents
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.SunPosition as DomainSunPosition
import studio.freestyle.labs.danjiangsunseeker.domain.physics.AtmosphericRefraction
import org.shredzone.commons.suncalc.SunPosition
import org.shredzone.commons.suncalc.SunTimes
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 包裝 commons-suncalc 函式庫 (v3.11)。
 *
 * Why commons-suncalc:
 *  - 純 JVM 實作 (Java 8+)、可離線使用
 *  - 演算法依照 Reda & Andreas (NOAA SPA) — 對 1900..2100 範圍誤差 < 0.0003°
 *  - SunTimes.VISUAL (預設) 已對 sunset 邊界做大氣折射修正
 *
 * 注意:
 *  - v3.x 起 `SunPosition.altitude` 為「幾何」仰角，**不**含大氣折射。
 *    本類別在 [positionAt] 內手動套用 [AtmosphericRefraction.apparentFromTrue]
 *    產生視仰角，避免上層使用者再算一次。
 *  - `elevation(m)` 對「視日落時間」非常重要：大屯山 1077m 的日落比海平面晚 ~5 分鐘
 *    (觀測者位置高 → 視野超過幾何地平線 → 太陽更晚沉入)。
 *  - SunPosition.azimuth: 從正北順時針 0..360°。
 */
@Singleton
class SunCalcDataSource @Inject constructor() {

    /**
     * 輕量版「視日落」事件：只算 visual sunset 時間 + 方位。
     *
     * [dailyEvents] 會額外計算黃金/藍調時刻 (共 3× SunTimes.compute())，
     * 但日曆掃描 (365 天 × N 熱點) 只需要日落時間與方位 — 其餘兩次純屬浪費。
     * 此方法只跑 1× SunTimes.compute()，把掃描的 SunTimes 成本砍到 1/3。
     */
    fun sunsetEvent(date: LocalDate, observer: GeoPoint): SunsetEvent {
        val startOfDay = date.atStartOfDay(TAIPEI)
        val visualTimes = SunTimes.compute()
            .on(startOfDay)
            .at(observer.latitude, observer.longitude)
            .elevation(observer.elevationMeters)
            .oneDay()
            .execute()
        val sunset = visualTimes.set
        return SunsetEvent(
            sunset = sunset,
            azimuthDegrees = sunset?.let { positionAt(it, observer).azimuthDegrees },
        )
    }

    /** 計算某瞬間從觀察者看太陽的位置 (含手動套用的大氣折射修正)。 */
    fun positionAt(time: ZonedDateTime, observer: GeoPoint): DomainSunPosition {
        val result = SunPosition.compute()
            .on(time)
            .at(observer.latitude, observer.longitude)
            .elevation(observer.elevationMeters)
            .execute()

        val trueAlt = result.altitude  // v3.x: 幾何仰角，未含折射
        val apparentAlt = AtmosphericRefraction.apparentFromTrue(trueAlt)
        return DomainSunPosition(
            time = time,
            azimuthDegrees = result.azimuth,
            altitudeDegrees = apparentAlt,
            trueAltitudeDegrees = trueAlt,
        )
    }

    /**
     * 計算指定日期某觀察者的所有關鍵太陽事件 (日出 / 日落 / 黃金時刻 / 藍調時刻 / 日落方位)。
     *
     * 時區固定 Asia/Taipei；觀察者高度若 > 0 m 會自動納入計算。
     */
    fun dailyEvents(date: LocalDate, observer: GeoPoint): DailySunEvents {
        val startOfDay = date.atStartOfDay(TAIPEI)

        val visualTimes = SunTimes.compute()
            .on(startOfDay)
            .at(observer.latitude, observer.longitude)
            .elevation(observer.elevationMeters)
            .oneDay()
            .execute()

        val goldenTimes = SunTimes.compute()
            .on(startOfDay)
            .at(observer.latitude, observer.longitude)
            .elevation(observer.elevationMeters)
            .twilight(SunTimes.Twilight.GOLDEN_HOUR)
            .oneDay()
            .execute()

        val blueTimes = SunTimes.compute()
            .on(startOfDay)
            .at(observer.latitude, observer.longitude)
            .elevation(observer.elevationMeters)
            .twilight(SunTimes.Twilight.BLUE_HOUR)
            .oneDay()
            .execute()

        val sunsetAzimuth = visualTimes.set?.let {
            positionAt(it, observer).azimuthDegrees
        }

        // 黃金時刻邊界:
        //   早晨: goldenTimes.rise (太陽穿越 GOLDEN_HOUR 線進入) → visualTimes.rise (日出)
        //   傍晚: visualTimes.set (日落)                          → goldenTimes.set (穿出 GOLDEN_HOUR 線)
        // 藍調時刻邊界:
        //   傍晚: blueTimes.rise (穿入)                            → blueTimes.set (穿出)
        return DailySunEvents(
            date = date,
            observer = observer,
            sunrise = visualTimes.rise,
            sunset = visualTimes.set,
            solarNoon = visualTimes.noon,
            goldenHourMorningStart = goldenTimes.rise,
            goldenHourMorningEnd = visualTimes.rise,
            goldenHourEveningStart = visualTimes.set,
            goldenHourEveningEnd = goldenTimes.set,
            blueHourEveningStart = blueTimes.rise,
            blueHourEveningEnd = blueTimes.set,
            sunsetAzimuthDegrees = sunsetAzimuth,
        )
    }

    private companion object {
        val TAIPEI: ZoneId = ZoneId.of("Asia/Taipei")
    }
}

/** [SunCalcDataSource.sunsetEvent] 的回傳值：視日落時間與方位 (極區無日落時兩者皆為 null)。 */
data class SunsetEvent(
    val sunset: ZonedDateTime?,
    val azimuthDegrees: Double?,
)
