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
import kotlin.math.abs
import kotlin.math.atan

/**
 * 「月亮穿塔」的目標仰角解析器 — [TowerTargetSunResolver] 的月亮版（付費功能）。
 *
 * 與太陽版不同：月亮一天內可能在**上升段 (月出後)** 與**下降段 (月落前)** 各穿越一次目標仰角
 * (塔頂 / 塔基相對觀察者的角高)。兩個穿越時刻可能分落橋的兩側，皆為可拍時機，因此這裡
 * 同時計算兩者，回傳「與觀察者→主塔方位最接近」(對齊偏差最小) 的那一個。當日上升、下降段
 * 皆無穿越時各值為 null。
 */
class TowerTargetMoonResolver @Inject constructor(
    private val moonCalc: MoonCalcDataSource,
) {
    fun resolve(date: LocalDate, observer: GeoPoint, target: TowerTarget): TowerTargetMoonEvent {
        val targetAlt = targetAltitude(observer, target)
        val times = moonCalc.moonTimes(date, observer)

        // 候選穿越時刻：月出後的上升穿越 (ascending=true) + 月落前的下降穿越 (ascending=false)；
        // 任一可能不存在。Pair.second 記錄該穿越屬月出段或月落段，供 UI 標示。
        val candidates = buildList {
            times.rise?.let { rise ->
                findCrossingAltitudeTime(
                    observer = observer,
                    targetAltitudeDegrees = targetAlt,
                    startTime = rise.minusMinutes(30),
                    endTime = rise.plusMinutes(180),
                    ascending = true,
                )?.let { add(it to true) }
            }
            times.set?.let { set ->
                findCrossingAltitudeTime(
                    observer = observer,
                    targetAltitudeDegrees = targetAlt,
                    startTime = set.minusMinutes(180),
                    endTime = set.plusMinutes(30),
                    ascending = false,
                )?.let { add(it to false) }
            }
        }
        if (candidates.isEmpty()) {
            return TowerTargetMoonEvent(
                time = null,
                azimuthDegrees = null,
                altitudeDegrees = null,
                ascending = null,
                targetAltitudeDegrees = targetAlt,
            )
        }

        // 與主塔方位最接近 (對齊偏差最小) 的穿越即為最佳拍攝時機。
        val towerBearing = Geodesy.inverse(observer, BridgeTower.position).initialBearingDegrees
        val (bestTime, bestAscending) = candidates.minByOrNull { (t, _) ->
            val az = moonCalc.moonPositionAt(t, observer).azimuthDegrees
            abs(Geodesy.signedAzimuthDelta(az, towerBearing))
        }!!
        val pos = moonCalc.moonPositionAt(bestTime, observer)
        return TowerTargetMoonEvent(
            time = bestTime,
            azimuthDegrees = pos.azimuthDegrees,
            altitudeDegrees = pos.altitudeDegrees,
            ascending = bestAscending,
            targetAltitudeDegrees = targetAlt,
        )
    }

    /**
     * 在 [startTime, endTime] 內找月亮穿越 [targetAltitudeDegrees] 的時刻。
     * [ascending] = true 表示上升段 (alt 隨時間遞增)、false 表示下降段 (alt 隨時間遞減)。
     * 目標仰角需落在窗口兩端之間，否則回傳 null。
     */
    private fun findCrossingAltitudeTime(
        observer: GeoPoint,
        targetAltitudeDegrees: Double,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
        ascending: Boolean,
    ): ZonedDateTime? {
        var lo = startTime
        var hi = endTime
        val loAlt = moonCalc.moonPositionAt(lo, observer).altitudeDegrees
        val hiAlt = moonCalc.moonPositionAt(hi, observer).altitudeDegrees
        if (ascending) {
            if (targetAltitudeDegrees !in loAlt..hiAlt) return null
        } else {
            if (targetAltitudeDegrees !in hiAlt..loAlt) return null
        }

        repeat(18) {
            val mid = lo.plusNanos(Duration.between(lo, hi).toNanos() / 2)
            val midAlt = moonCalc.moonPositionAt(mid, observer).altitudeDegrees
            // 上升段：mid 偏低 → 往右收；下降段：mid 偏高 → 往右收。
            val midBelowTarget = if (ascending) midAlt < targetAltitudeDegrees else midAlt > targetAltitudeDegrees
            if (midBelowTarget) lo = mid else hi = mid
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
    /** true = 月出段 (上升穿塔)、false = 月落段 (下降穿塔)、null = 當日無穿越。 */
    val ascending: Boolean?,
    val targetAltitudeDegrees: Double,
)
