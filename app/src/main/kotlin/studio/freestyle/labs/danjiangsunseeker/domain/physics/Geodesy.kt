package studio.freestyle.labs.danjiangsunseeker.domain.physics

import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * WGS-84 球面 / 橢球體幾何運算。
 *
 * 提供:
 *  - Vincenty 反算 (inverse): 兩點 → 大圓距離 + 初始 / 終末方位角
 *  - Vincenty 正算 (direct): 起點 + 距離 + 方位角 → 終點
 *  - Haversine: 純球面距離，作為 Vincenty 不收斂時的 fallback
 *
 * Why Vincenty 而非 Haversine: 淡江大橋到大屯山約 8 km，到八里約 1.3 km — Haversine 已足夠 (誤差 < 1m)，
 * 但 Vincenty 對未來擴展（如新北至九份遠景視角）保留精度。
 */
object Geodesy {

    private const val A = 6_378_137.0                  // WGS-84 半長軸 (m)
    private const val F = 1.0 / 298.257_223_563       // 扁率
    private const val B = (1.0 - F) * A                // 半短軸 (m)

    /** 兩點測地計算結果 */
    data class GeodesicResult(
        val distanceMeters: Double,
        val initialBearingDegrees: Double,  // from p1 looking at p2
        val finalBearingDegrees: Double,    // direction at p2 having travelled from p1
    )

    /**
     * Vincenty inverse formula。對近距離與全球範圍皆收斂；
     * 退化情形（兩點為對極點）回傳 Haversine 結果。
     */
    fun inverse(p1: GeoPoint, p2: GeoPoint): GeodesicResult {
        val φ1 = p1.latitude.toRadians()
        val φ2 = p2.latitude.toRadians()
        val L = (p2.longitude - p1.longitude).toRadians()
        val U1 = atan((1 - F) * tan(φ1))
        val U2 = atan((1 - F) * tan(φ2))
        val sinU1 = sin(U1); val cosU1 = cos(U1)
        val sinU2 = sin(U2); val cosU2 = cos(U2)

        var λ = L
        var sinλ: Double; var cosλ: Double
        var sinσ: Double; var cosσ: Double; var σ: Double
        var sinα: Double; var cos2α: Double; var cos2σm: Double; var C: Double

        var iterations = 0
        do {
            sinλ = sin(λ); cosλ = cos(λ)
            sinσ = sqrt((cosU2 * sinλ).pow(2) +
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosλ).pow(2))
            if (sinσ == 0.0) {
                return GeodesicResult(0.0, 0.0, 0.0) // 同一點
            }
            cosσ = sinU1 * sinU2 + cosU1 * cosU2 * cosλ
            σ = atan2(sinσ, cosσ)
            sinα = cosU1 * cosU2 * sinλ / sinσ
            cos2α = 1 - sinα * sinα
            cos2σm = if (cos2α == 0.0) 0.0 else cosσ - 2 * sinU1 * sinU2 / cos2α  // 赤道線
            C = F / 16 * cos2α * (4 + F * (4 - 3 * cos2α))
            val λPrev = λ
            λ = L + (1 - C) * F * sinα * (σ + C * sinσ *
                    (cos2σm + C * cosσ * (-1 + 2 * cos2σm * cos2σm)))
            if (abs(λ - λPrev) < 1e-12) break
        } while (++iterations < 100)

        if (iterations >= 100) {
            // 不收斂 → 回退 Haversine
            val d = haversineDistanceMeters(p1, p2)
            val bearing = initialBearingDegrees(p1, p2)
            return GeodesicResult(d, bearing, (bearing + 180.0) % 360.0)
        }

        val u2 = cos2α * (A * A - B * B) / (B * B)
        val A1 = 1 + u2 / 16384 * (4096 + u2 * (-768 + u2 * (320 - 175 * u2)))
        val B1 = u2 / 1024 * (256 + u2 * (-128 + u2 * (74 - 47 * u2)))
        val Δσ = B1 * sinσ * (cos2σm + B1 / 4 *
                (cosσ * (-1 + 2 * cos2σm * cos2σm) -
                        B1 / 6 * cos2σm * (-3 + 4 * sinσ * sinσ) * (-3 + 4 * cos2σm * cos2σm)))
        val distance = B * A1 * (σ - Δσ)

        val initialBearing = atan2(cosU2 * sinλ, cosU1 * sinU2 - sinU1 * cosU2 * cosλ).toDegrees()
        val finalBearing = atan2(cosU1 * sinλ, -sinU1 * cosU2 + cosU1 * sinU2 * cosλ).toDegrees()

        return GeodesicResult(
            distanceMeters = distance,
            initialBearingDegrees = (initialBearing + 360.0) % 360.0,
            finalBearingDegrees = (finalBearing + 360.0) % 360.0,
        )
    }

    /**
     * Vincenty direct formula。給定起點、距離、初始方位 → 終點 + 終末方位。
     */
    fun direct(start: GeoPoint, bearingDegrees: Double, distanceMeters: Double): GeoPoint {
        val α1 = bearingDegrees.toRadians()
        val sinα1 = sin(α1); val cosα1 = cos(α1)
        val U1 = atan((1 - F) * tan(start.latitude.toRadians()))
        val sinU1 = sin(U1); val cosU1 = cos(U1)
        val σ1 = atan2(tan(U1), cosα1)
        val sinα = cosU1 * sinα1
        val cos2α = 1 - sinα * sinα
        val u2 = cos2α * (A * A - B * B) / (B * B)
        val A1 = 1 + u2 / 16384 * (4096 + u2 * (-768 + u2 * (320 - 175 * u2)))
        val B1 = u2 / 1024 * (256 + u2 * (-128 + u2 * (74 - 47 * u2)))

        var σ = distanceMeters / (B * A1)
        var sinσ: Double; var cosσ: Double; var cos2σm: Double
        var iterations = 0
        do {
            cos2σm = cos(2 * σ1 + σ)
            sinσ = sin(σ); cosσ = cos(σ)
            val Δσ = B1 * sinσ * (cos2σm + B1 / 4 *
                    (cosσ * (-1 + 2 * cos2σm * cos2σm) -
                            B1 / 6 * cos2σm * (-3 + 4 * sinσ * sinσ) * (-3 + 4 * cos2σm * cos2σm)))
            val σPrev = σ
            σ = distanceMeters / (B * A1) + Δσ
            if (abs(σ - σPrev) < 1e-12) break
        } while (++iterations < 100)

        val φ2 = atan2(
            sinU1 * cosσ + cosU1 * sinσ * cosα1,
            (1 - F) * sqrt(sinα * sinα + (sinU1 * sinσ - cosU1 * cosσ * cosα1).pow(2)),
        )
        val λ = atan2(
            sinσ * sinα1,
            cosU1 * cosσ - sinU1 * sinσ * cosα1,
        )
        val C = F / 16 * cos2α * (4 + F * (4 - 3 * cos2α))
        val L = λ - (1 - C) * F * sinα *
                (σ + C * sinσ * (cos2σm + C * cosσ * (-1 + 2 * cos2σm * cos2σm)))
        val λ2 = start.longitude.toRadians() + L

        return GeoPoint(
            latitude = φ2.toDegrees(),
            longitude = ((λ2.toDegrees() + 540.0) % 360.0) - 180.0,  // normalize to -180..180
            elevationMeters = start.elevationMeters,
        )
    }

    /** Haversine 距離（球面，作為 Vincenty fallback 或對近距離的速算）。 */
    fun haversineDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val R = 6_371_008.8  // WGS-84 平均地球半徑 (m)
        val φ1 = p1.latitude.toRadians()
        val φ2 = p2.latitude.toRadians()
        val Δφ = (p2.latitude - p1.latitude).toRadians()
        val Δλ = (p2.longitude - p1.longitude).toRadians()
        val a = sin(Δφ / 2).pow(2) + cos(φ1) * cos(φ2) * sin(Δλ / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    /** 初始方位（從 p1 看 p2，0..360°）。 */
    fun initialBearingDegrees(p1: GeoPoint, p2: GeoPoint): Double {
        val φ1 = p1.latitude.toRadians()
        val φ2 = p2.latitude.toRadians()
        val Δλ = (p2.longitude - p1.longitude).toRadians()
        val y = sin(Δλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        return (atan2(y, x).toDegrees() + 360.0) % 360.0
    }

    /**
     * 計算太陽從觀測者看主塔的「方位偏差」— 即太陽方位角 − 觀測者→主塔方位。
     * 結果 ∈ (-180, 180]，正值代表太陽位於主塔的右側 (順時針)。
     *
     * |offset| < towerAngularWidth/2 即為「穿塔」。
     */
    fun signedAzimuthDelta(sunAzimuth: Double, observerToTargetBearing: Double): Double {
        var d = sunAzimuth - observerToTargetBearing
        d = ((d + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return d
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
    private fun Double.toDegrees(): Double = this * 180.0 / PI
}
