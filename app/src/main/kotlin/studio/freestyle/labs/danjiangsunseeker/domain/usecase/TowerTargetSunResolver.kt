package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import studio.freestyle.labs.danjiangsunseeker.data.astro.SunCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.atan

class TowerTargetSunResolver @Inject constructor(
    private val sunCalc: SunCalcDataSource,
) {
    fun resolve(date: LocalDate, observer: GeoPoint, target: TowerTarget): TowerTargetSunEvent {
        // 只取「視日落」：略過 dailyEvents 內的黃金/藍調時刻計算 (掃描熱點時用不到)。
        val event = sunCalc.sunsetEvent(date, observer)
        val sunset = event.sunset
        if (sunset == null) {
            return TowerTargetSunEvent(
                time = null,
                azimuthDegrees = event.azimuthDegrees,
                altitudeDegrees = null,
                targetAltitudeDegrees = targetAltitude(observer, target),
            )
        }

        val targetAlt = targetAltitude(observer, target)
        val targetTime = findDescendingAltitudeTime(
            observer = observer,
            targetAltitudeDegrees = targetAlt,
            endTime = sunset.plusMinutes(30),
            startTime = sunset.minusMinutes(180),
        ) ?: sunset
        val pos = sunCalc.positionAt(targetTime, observer)
        return TowerTargetSunEvent(
            time = targetTime,
            azimuthDegrees = pos.azimuthDegrees,
            altitudeDegrees = pos.altitudeDegrees,
            targetAltitudeDegrees = targetAlt,
        )
    }

    private fun findDescendingAltitudeTime(
        observer: GeoPoint,
        targetAltitudeDegrees: Double,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
    ): ZonedDateTime? {
        var lo = startTime
        var hi = endTime
        val loAlt = sunCalc.positionAt(lo, observer).altitudeDegrees
        val hiAlt = sunCalc.positionAt(hi, observer).altitudeDegrees
        if (targetAltitudeDegrees !in hiAlt..loAlt) return null

        // 16 次二分把 210 分鐘窗口收斂到 ~0.2 秒 (太陽方位誤差 < 0.001°，遠小於對齊容差)。
        // 原本 22 次 (≈微秒級) 純屬浪費 — 每對 (日期×熱點) 省下 6 次 positionAt。
        repeat(16) {
            val mid = lo.plusNanos(java.time.Duration.between(lo, hi).toNanos() / 2)
            val midAlt = sunCalc.positionAt(mid, observer).altitudeDegrees
            if (midAlt > targetAltitudeDegrees) lo = mid else hi = mid
        }
        return hi
    }

    private fun targetAltitude(observer: GeoPoint, target: TowerTarget): Double {
        val distance = Geodesy.inverse(observer, BridgeTower.position).distanceMeters.coerceAtLeast(1.0)
        return Math.toDegrees(atan((target.elevationMeters - observer.elevationMeters) / distance))
    }
}

data class TowerTargetSunEvent(
    val time: ZonedDateTime?,
    val azimuthDegrees: Double?,
    val altitudeDegrees: Double?,
    val targetAltitudeDegrees: Double,
)
