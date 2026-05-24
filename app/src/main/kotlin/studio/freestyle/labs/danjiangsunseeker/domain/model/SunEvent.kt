package studio.freestyle.labs.danjiangsunseeker.domain.model

import java.time.ZonedDateTime

/**
 * 觀察點某瞬間的太陽位置。
 *
 * @param azimuthDegrees 太陽方位角 (0..360°, 從正北順時針)
 * @param altitudeDegrees 太陽仰角，含大氣折射修正 (Apparent altitude)
 * @param trueAltitudeDegrees 不含大氣折射的幾何仰角，用於需嚴格幾何計算的場景
 */
data class SunPosition(
    val time: ZonedDateTime,
    val azimuthDegrees: Double,
    val altitudeDegrees: Double,
    val trueAltitudeDegrees: Double,
)

/**
 * 某日某觀察點的關鍵太陽事件。所有時間均為 [java.time.ZoneId.of] "Asia/Taipei" 區。
 */
data class DailySunEvents(
    val date: java.time.LocalDate,
    val observer: GeoPoint,
    val sunrise: ZonedDateTime?,
    val sunset: ZonedDateTime?,
    val solarNoon: ZonedDateTime?,
    /** 黃金時刻起點（早晚各一段） */
    val goldenHourMorningStart: ZonedDateTime?,
    val goldenHourMorningEnd: ZonedDateTime?,
    val goldenHourEveningStart: ZonedDateTime?,
    val goldenHourEveningEnd: ZonedDateTime?,
    /** 藍調時刻 (民用曙暮光終末) */
    val blueHourEveningStart: ZonedDateTime?,
    val blueHourEveningEnd: ZonedDateTime?,
    /** 日落瞬間的太陽方位角（觀察者高度修正後） */
    val sunsetAzimuthDegrees: Double?,
)

/**
 * 太陽軌跡上的一個取樣點。用於熱點縮圖呈現「日落前最後一段時間」的方位 + 仰角變化。
 *
 * @param minutesBeforeSunset 距日落還剩幾分鐘 (0 = 日落瞬間)
 */
data class SunTrailPoint(
    val minutesBeforeSunset: Int,
    val azimuthDegrees: Double,
    val altitudeDegrees: Double,
)
