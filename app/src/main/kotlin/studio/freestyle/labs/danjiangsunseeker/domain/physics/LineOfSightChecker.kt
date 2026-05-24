package studio.freestyle.labs.danjiangsunseeker.domain.physics

import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint

/**
 * 視線遮蔽 (Line of Sight) 檢查。
 *
 * Why: APP 可能會推薦一個幾何上絕佳的位置，但現場被防風林、新建案或山體擋住。
 * 本物件接受沿路徑採樣的高程資料，判斷觀測者看目標時是否有任何中間點突出於視線之上。
 *
 * 採樣高程需由 [studio.freestyle.labs.danjiangsunseeker.data.elevation.ElevationDataSource] 提供
 * (例：Google Elevation API、SRTM 30m 離線檔)。本物件純為演算法，不做網路。
 */
object LineOfSightChecker {

    /**
     * 沿觀測者 → 目標的路徑採樣，判斷視線是否被任何中間點遮蔽。
     *
     * @param observer 觀測者座標 (含 elevation)
     * @param target 目標座標 (含 elevation — 例如塔尖)
     * @param samples 中間採樣點 (point + ground elevation in metres)，依距離由近到遠排序
     * @param earthCurvatureCorrection 是否啟用地球曲率折射修正 (對 > ~3 km 視線才有意義)
     */
    fun isVisible(
        observer: GeoPoint,
        target: GeoPoint,
        samples: List<Sample>,
        earthCurvatureCorrection: Boolean = true,
    ): VisibilityResult {
        val totalDistance = Geodesy.haversineDistanceMeters(observer, target)
        if (totalDistance == 0.0) return VisibilityResult(visible = true, firstBlockerDistanceMeters = null)

        val observerElev = observer.elevationMeters + EYE_HEIGHT_M
        val targetElev = target.elevationMeters

        // 視線從觀測者到目標的「俯仰角」隨距離線性內插
        for (sample in samples) {
            val d = sample.distanceFromObserverMeters
            if (d <= 0.0 || d >= totalDistance) continue

            val ratio = d / totalDistance
            val lineOfSightElev = observerElev + (targetElev - observerElev) * ratio

            // 地球曲率：對遠距離視線，遠處的地表會「下沉」h = d * (totalDistance - d) / (2 * R_eff)
            val earthDrop = if (earthCurvatureCorrection) {
                d * (totalDistance - d) / (2.0 * EFFECTIVE_EARTH_RADIUS_M)
            } else 0.0
            val effectiveTerrainElev = sample.terrainElevationMeters - earthDrop

            if (effectiveTerrainElev > lineOfSightElev) {
                return VisibilityResult(visible = false, firstBlockerDistanceMeters = d)
            }
        }
        return VisibilityResult(visible = true, firstBlockerDistanceMeters = null)
    }

    /** 路徑採樣點 */
    data class Sample(
        val point: GeoPoint,
        val distanceFromObserverMeters: Double,
        val terrainElevationMeters: Double,
    )

    data class VisibilityResult(
        val visible: Boolean,
        val firstBlockerDistanceMeters: Double?,
    )

    /** 假設站立者眼睛高度 (m)。 */
    private const val EYE_HEIGHT_M: Double = 1.65

    /**
     * 地球折射有效半徑 — 標準大氣下 k ≈ 1.13，所以有效半徑 ≈ R / (1 - 1/k) ≈ 7860 km。
     * 採 7_860_000 m 作為典型值。
     */
    private const val EFFECTIVE_EARTH_RADIUS_M: Double = 7_860_000.0

    /**
     * 工具：產生「觀測者 → 目標」之間的等距採樣點 (大圓內插，含 fractional bearing)。
     * 採樣間距 [stepMeters]，最後一個點可能略小於 stepMeters。
     */
    fun sampleGreatCirclePath(
        observer: GeoPoint,
        target: GeoPoint,
        stepMeters: Double = 50.0,
    ): List<GeoPoint> {
        val total = Geodesy.haversineDistanceMeters(observer, target)
        if (total <= stepMeters) return emptyList()
        val bearing = Geodesy.initialBearingDegrees(observer, target)
        val numSteps = (total / stepMeters).toInt()
        return (1 until numSteps).map { i ->
            Geodesy.direct(observer, bearing, stepMeters * i)
        }
    }
}
