package studio.freestyle.labs.danjiangsunseeker.domain.model

/**
 * 地理座標 (WGS-84)。
 *
 * @param latitude  緯度 (degrees, -90..90)
 * @param longitude 經度 (degrees, -180..180)
 * @param elevationMeters 觀測點海拔高度，公尺。對大屯山 (1077m) 觀景台這類非海平面測點不可省略，
 *                        否則太陽方位 / 仰角計算會出現可觀誤差。
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double = 0.0,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude out of range: $longitude" }
    }
}
