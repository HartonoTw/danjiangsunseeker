package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import javax.inject.Inject
import kotlin.math.atan
import kotlin.math.tan

/**
 * 鏡頭焦段構圖模擬。
 *
 * 給定觀察者座標、感光元件規格、焦距 → 計算:
 *   - 水平 / 垂直 FOV
 *   - 主塔在畫面寬度的佔比
 *   - 太陽在畫面寬度的佔比 (太陽角直徑 ≈ 0.53°)
 *   - 主塔在畫面高度的佔比 (含塔尖高度與觀察者高度差)
 *
 * Why: 廣角鏡頭下太陽只是一點；600mm 望遠則太陽和橋塔一樣大。
 * 攝影師需要這個資訊決定當天該帶哪顆鏡頭。
 */
class SimulateFocalLengthUseCase @Inject constructor() {

    operator fun invoke(
        observer: GeoPoint,
        focalLengthMm: Double,
        sensor: SensorSpec,
    ): FocalSimulationResult {
        val geodesic = Geodesy.inverse(observer, BridgeTower.position)
        val distance = geodesic.distanceMeters

        val fovH = 2.0 * Math.toDegrees(atan((sensor.widthMm / 2.0) / focalLengthMm))
        val fovV = 2.0 * Math.toDegrees(atan((sensor.heightMm / 2.0) / focalLengthMm))

        // 主塔角寬 / 角高 (從觀察者視角)
        val towerAngularWidth = 2.0 * Math.toDegrees(
            atan((BridgeTower.TOWER_WIDTH_M / 2.0) / distance)
        )
        val verticalSpan = BridgeTower.TOWER_TIP_ELEVATION_M - observer.elevationMeters
        val towerAngularHeight = Math.toDegrees(atan(verticalSpan / distance))

        val sunAngularDiameter = SUN_ANGULAR_DIAMETER_DEG

        return FocalSimulationResult(
            distanceToTowerMeters = distance,
            horizontalFovDegrees = fovH,
            verticalFovDegrees = fovV,
            towerWidthFractionOfFrame = towerAngularWidth / fovH,
            towerHeightFractionOfFrame = towerAngularHeight / fovV,
            sunWidthFractionOfFrame = sunAngularDiameter / fovH,
            recommendation = recommend(towerAngularWidth, fovH),
        )
    }

    private fun recommend(towerWidthDeg: Double, fovH: Double): String {
        val frac = towerWidthDeg / fovH
        return when {
            frac > 0.4 -> "主塔過大，建議改用更廣角鏡頭"
            frac < 0.02 -> "主塔太小，建議改用更長焦鏡頭 (200mm+)"
            else -> "構圖比例合理"
        }
    }

    /** 太陽 (與滿月) 視角直徑 (deg)。 */
    private companion object {
        const val SUN_ANGULAR_DIAMETER_DEG: Double = 0.53
    }
}

data class FocalSimulationResult(
    val distanceToTowerMeters: Double,
    val horizontalFovDegrees: Double,
    val verticalFovDegrees: Double,
    val towerWidthFractionOfFrame: Double,
    val towerHeightFractionOfFrame: Double,
    val sunWidthFractionOfFrame: Double,
    val recommendation: String,
)

/**
 * 常見感光元件規格。
 */
data class SensorSpec(
    val displayName: String,
    val widthMm: Double,
    val heightMm: Double,
) {
    companion object {
        val FULL_FRAME = SensorSpec("全片幅 35mm", 36.0, 24.0)
        val APS_C = SensorSpec("APS-C", 23.5, 15.6)
        val MICRO_FOUR_THIRDS = SensorSpec("M4/3", 17.3, 13.0)
        val ONE_INCH = SensorSpec("1 吋", 13.2, 8.8)
        val PHONE_MAIN = SensorSpec("手機主鏡頭 (1/1.3\")", 9.8, 7.3)

        val ALL = listOf(FULL_FRAME, APS_C, MICRO_FOUR_THIRDS, ONE_INCH, PHONE_MAIN)
    }
}
