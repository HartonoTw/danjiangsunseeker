package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import studio.freestyle.labs.danjiangsunseeker.R
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
        )
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
)

/**
 * 常見感光元件規格。
 *
 * @param id 穩定識別字串（不隨語系變動，用於選取/比對）。
 * @param labelRes 顯示用的本地化字串資源。
 */
data class SensorSpec(
    val id: String,
    val labelRes: Int,
    val widthMm: Double,
    val heightMm: Double,
) {
    companion object {
        val FULL_FRAME = SensorSpec("full_frame", R.string.sensor_full_frame, 36.0, 24.0)
        val APS_C = SensorSpec("aps_c", R.string.sensor_aps_c, 23.5, 15.6)
        val MICRO_FOUR_THIRDS = SensorSpec("m43", R.string.sensor_m43, 17.3, 13.0)
        val ONE_INCH = SensorSpec("one_inch", R.string.sensor_one_inch, 13.2, 8.8)
        val PHONE_MAIN = SensorSpec("phone_main", R.string.sensor_phone_main, 9.8, 7.3)

        val ALL = listOf(FULL_FRAME, APS_C, MICRO_FOUR_THIRDS, ONE_INCH, PHONE_MAIN)
    }
}
