package studio.freestyle.labs.danjiangsunseeker.domain.model

import java.time.ZonedDateTime

/**
 * 「黃金拍攝帶」— 從主塔出發、沿反方位射線上的測地路徑。
 *
 * @param fromTower 主塔座標
 * @param bearingFromTowerDegrees 射線從主塔出發的方位角（= 日落方位角，0..360°）
 * @param sampledPoints 沿射線採樣的座標點 (依距離由近到遠排列，最遠 [maxRangeKm])
 * @param maxRangeKm 採樣最遠距離
 * @param eventTime 對應太陽穿塔的瞬間
 */
data class GoldenLine(
    val fromTower: GeoPoint,
    val bearingFromTowerDegrees: Double,
    val sampledPoints: List<GoldenLinePoint>,
    val maxRangeKm: Double,
    val eventTime: ZonedDateTime,
)

/**
 * 黃金拍攝帶上單一採樣點。
 *
 * @param point 該點座標
 * @param distanceFromTowerMeters 距主塔距離
 * @param isBlocked 視線是否被地形或建築物遮蔽（需 LineOfSightChecker 計算）
 * @param towerAngularWidthDegrees 主塔在此距離下的水平角寬，等於使用者拍攝時可容忍的方位誤差上界
 */
data class GoldenLinePoint(
    val point: GeoPoint,
    val distanceFromTowerMeters: Double,
    val isBlocked: Boolean = false,
    val towerAngularWidthDegrees: Double = 0.0,
)
