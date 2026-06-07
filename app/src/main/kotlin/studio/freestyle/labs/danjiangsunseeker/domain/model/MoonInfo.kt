package studio.freestyle.labs.danjiangsunseeker.domain.model

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * 觀察點某瞬間的月亮位置。
 *
 * @param azimuthDegrees 月亮方位角 (0..360°, 從正北順時針)
 * @param altitudeDegrees 視仰角，含大氣折射修正 (apparent altitude)
 * @param trueAltitudeDegrees 不含大氣折射的幾何仰角
 * @param distanceKm 地心至月心距離 (km)，可用於估算視角直徑 (近地點較大)
 */
data class MoonPosition(
    val time: ZonedDateTime,
    val azimuthDegrees: Double,
    val altitudeDegrees: Double,
    val trueAltitudeDegrees: Double,
    val distanceKm: Double,
)

/**
 * 八個主要月相。亮面比例 + 盈虧 (waxing) 共同決定。
 *
 * 上弦 (FIRST_QUARTER) = 右半亮、下弦 (LAST_QUARTER) = 左半亮 (北半球視角)。
 */
enum class LunarPhase {
    NEW,             // 朔 (新月)
    WAXING_CRESCENT, // 眉月 (盈)
    FIRST_QUARTER,   // 上弦 (右半亮)
    WAXING_GIBBOUS,  // 盈凸月
    FULL,            // 望 (滿月)
    WANING_GIBBOUS,  // 虧凸月
    LAST_QUARTER,    // 下弦 (左半亮)
    WANING_CRESCENT, // 殘月 (虧)
}

/**
 * 某日某觀察點的月亮資訊。時間皆為 Asia/Taipei 區。
 *
 * @param fractionLit 亮面比例 0.0 (朔) .. 1.0 (望)
 * @param waxing true = 盈 (亮面增加中)、false = 虧
 * @param azimuthAtSet 月落瞬間方位角 (供地圖「月亮黃金帶」用)；無月落時為 null
 */
data class MoonInfo(
    val date: LocalDate,
    val observer: GeoPoint,
    val rise: ZonedDateTime?,
    val set: ZonedDateTime?,
    val fractionLit: Double,
    val waxing: Boolean,
    val phase: LunarPhase,
    val azimuthAtSet: Double?,
)
