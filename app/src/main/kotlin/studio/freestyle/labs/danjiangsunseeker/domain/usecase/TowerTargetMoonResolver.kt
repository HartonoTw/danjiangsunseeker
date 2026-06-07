package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import studio.freestyle.labs.danjiangsunseeker.data.astro.MoonCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.atan

/**
 * 「月亮穿塔」的目標仰角解析器 — [TowerTargetSunResolver] 的月亮版（付費功能）。
 *
 * 與太陽版相反：以**月出 (moonrise)** 為錨點，找月亮在月出後「上升」穿越目標仰角
 * (塔頂 / 塔基相對觀察者的角高) 的瞬間，並回傳該瞬間的月亮方位。觀察者→主塔方位與此方位
 * 比較即得對齊偏差。當日無月出時各值為 null。
 */
class TowerTargetMoonResolver @Inject constructor(
    private val moonCalc: MoonCalcDataSource,
) {
    fun resolve(date: LocalDate, observer: GeoPoint, target: TowerTarget): TowerTargetMoonEvent {
        val targetAlt = targetAltitude(observer, target)
        val moonrise = moonCalc.moonTimes(date, observer).rise
            ?: return TowerTargetMoonEvent(
                time = null,
                azimuthDegrees = null,
                altitudeDegrees = null,
                targetAltitudeDegrees = targetAlt,
            )

        val targetTime = findAscendingAltitudeTime(
            observer = observer,
            targetAltitudeDegrees = targetAlt,
            startTime = moonrise.minusMinutes(30),
            endTime = moonrise.plusMinutes(180),
        ) ?: moonrise
        val pos = moonCalc.moonPositionAt(targetTime, observer)
        return TowerTargetMoonEvent(
            time = targetTime,
            azimuthDegrees = pos.azimuthDegrees,
            altitudeDegrees = pos.altitudeDegrees,
            targetAltitudeDegrees = targetAlt,
        )
    }

    /** 在 [startTime, endTime] 內找月亮「上升」穿越 [targetAltitudeDegrees] 的時刻 (alt 隨時間遞增)。 */
    private fun findAscendingAltitudeTime(
        observer: GeoPoint,
        targetAltitudeDegrees: Double,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
    ): ZonedDateTime? {
        var lo = startTime
        var hi = endTime
        val loAlt = moonCalc.moonPositionAt(lo, observer).altitudeDegrees
        val hiAlt = moonCalc.moonPositionAt(hi, observer).altitudeDegrees
        // 上升段：起點仰角低、終點仰角高；目標需落在兩者之間
        if (targetAltitudeDegrees !in loAlt..hiAlt) return null

        repeat(18) {
            val mid = lo.plusNanos(Duration.between(lo, hi).toNanos() / 2)
            val midAlt = moonCalc.moonPositionAt(mid, observer).altitudeDegrees
            if (midAlt < targetAltitudeDegrees) lo = mid else hi = mid
        }
        return hi
    }

    private fun targetAltitude(observer: GeoPoint, target: TowerTarget): Double {
        val distance = Geodesy.inverse(observer, BridgeTower.position).distanceMeters.coerceAtLeast(1.0)
        return Math.toDegrees(atan((target.elevationMeters - observer.elevationMeters) / distance))
    }
}

data class TowerTargetMoonEvent(
    val time: ZonedDateTime?,
    val azimuthDegrees: Double?,
    val altitudeDegrees: Double?,
    val targetAltitudeDegrees: Double,
)
