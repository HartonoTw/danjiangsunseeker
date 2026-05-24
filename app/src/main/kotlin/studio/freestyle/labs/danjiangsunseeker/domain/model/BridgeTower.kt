package studio.freestyle.labs.danjiangsunseeker.domain.model

/**
 * 淡江大橋主塔基準座標。
 *
 * Why: 本 APP 所有幾何運算（黃金拍攝帶、太陽相對方位、AR 錨點）均以此為原點。
 * 數值來源為公路總局公告 + 維基百科估算，**通車後需以實測 GPS 校正**，
 * 並開放使用者回報座標誤差。
 *
 * Coordinate convention: WGS-84.
 */
object BridgeTower {
    const val LATITUDE: Double = 25.17531
    const val LONGITUDE: Double = 121.41778

    /**
     * 主塔基座海拔 (m)。
     * Note: 實際塔基在水下 EL -11.4m，但繪圖/AR 中以「水面下露出」之最低可見點為基準，
     * 用一個略高於水面的近似值即可。詳見 BASE_TRUE_ELEVATION_M。
     */
    const val BASE_ELEVATION_M: Double = 30.0

    /** 真實塔基海拔（水下，EL -11.4m）。用於工程數據展示，不用於繪圖。 */
    const val BASE_TRUE_ELEVATION_M: Double = -11.4

    /** 主塔水面以上露出高度 (m)：塔頂 EL 200m，從水面算起 200m。 */
    const val TOWER_TIP_ELEVATION_M: Double = 200.0

    /** 主塔總高 (m)：從水下塔基 EL -11.4 到塔頂 EL 200 ≈ 211m。 */
    const val TOWER_HEIGHT_M: Double = 211.0

    /** 橋面（甲板）海拔 (m)：水面以上 ~20m（橋下淨空高）。*/
    const val DECK_ELEVATION_M: Double = 20.0

    /** A 字塔兩柱「合體點」海拔 (m)：水面以上 72m。72m 以上為單柱、以下為兩柱張開。 */
    const val A_FRAME_JOIN_ELEVATION_M: Double = 72.0

    /** 主塔水平寬度估算 (m)。用於決定「夕陽穿塔」的可接受方位角容差。 */
    const val TOWER_WIDTH_M: Double = 12.0

    // ── 橋樑跨度與軸向 ─────────────────────────────────────────────
    /** 主跨長度 (m)：主塔朝八里端（西南），世界最長單塔不對稱斜張橋主跨。 */
    const val MAIN_SPAN_M: Double = 450.0

    /** 側跨長度 (m)：主塔朝淡水端（東北）。*/
    const val SIDE_SPAN_M: Double = 175.0

    /** 八里端最長鋼索掛點到塔的水平距離 (m)。約佔主跨 91%（410/450）。 */
    const val BALI_LONGEST_STAY_M: Double = 410.0

    /**
     * 淡水端最長鋼索掛點到塔的水平距離 (m)。
     * Note: 320m 超出側跨 175m，延伸到引橋區段內（175 + 145 ≈ 320），與真實工程數據一致。
     */
    const val TAMSUI_LONGEST_STAY_M: Double = 320.0

    /** 八里端引橋總長 (m)：主跨 450m 之外，再延伸 75 + 75m 兩段引橋。 */
    const val BALI_APPROACH_TOTAL_M: Double = 150.0

    /** 淡水端引橋總長 (m)：側跨 175m 之外，再延伸 75 + 70m 兩段引橋。 */
    const val TAMSUI_APPROACH_TOTAL_M: Double = 145.0

    /**
     * 從主塔朝八里端（主跨方向）的方位角，WGS-84 順時針自正北。
     * 由塔座標 (25.17531°N, 121.41778°E) 與橋端點幾何反算，
     * 淡水端在反方向 BALI_BEARING_DEG + 180° ≈ 61°。
     */
    const val BALI_BEARING_DEG: Double = 241.0

    val position: GeoPoint = GeoPoint(LATITUDE, LONGITUDE, BASE_ELEVATION_M)
}
