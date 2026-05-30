package studio.freestyle.labs.danjiangsunseeker.domain.model

import studio.freestyle.labs.danjiangsunseeker.R

/**
 * 內建熱點清單。所有熱點皆通過「主塔方位 vs 夕陽方位 ≤ 90°」可同框驗證。
 * 太陽方位在台灣全年只在 243°-298° 之間擺盪；主塔方位超出此範圍 90° 以上的點
 * (主塔與夕陽必然不在同一視角範圍) 已剔除。
 */
object DefaultHotspots {

    // ============== 北岸 (淡水側) — 由西到東 ==============

    val STARBUCKS = Hotspot(
        id = "starbucks",
        nameRes = R.string.hotspot_starbucks,
        customName = null,
        position = GeoPoint(25.17145379252861, 121.4370207680405, elevationMeters = 6.0),
        description = "淡水河岸星巴克，玻璃窗景觀，距主塔約 2.0 km，主塔在西北西方向 (約 283°)，" +
            "**夏至前後最有機會看到夕陽穿塔**。閒適視角，等不到完美對齊時最舒服的退路。",
    )

    val CUSTOMS_WHARF = Hotspot(
        id = "customs_wharf",
        nameRes = R.string.hotspot_customs_wharf,
        customName = null,
        position = GeoPoint(25.174439762352144, 121.43177818353163, elevationMeters = 3.0),
        description = "海關碼頭歷史古蹟，距主塔約 1.4 km。主塔位於正西方 (~274°)，" +
            "**每年兩個對齊窗口：約 4 月初與 9 月中**（春分後 / 秋分前太陽方位通過 274°）。" +
            "古蹟磚牆 + 大橋日落構圖極具歷史感。",
    )

    val MACKAY_LANDING = Hotspot(
        id = "mackay_landing",
        nameRes = R.string.hotspot_mackay_landing,
        customName = null,
        position = GeoPoint(25.171056, 121.437405, elevationMeters = 20.6),
        description = "馬偕上岸處，距主塔約 2.0 km。主塔位於 WNW (~283°)，" +
            "與淡水河岸其他點同屬**夏至前後（5 月底、7 月底）**的高方位對齊窗口。",
    )

    val TAMSUI_FERRY = Hotspot(
        id = "tamsui_ferry",
        nameRes = R.string.hotspot_tamsui_ferry,
        customName = null,
        position = GeoPoint(25.17000726568226, 121.43877415755742, elevationMeters = 3.0),
        description = "淡水老街碼頭，距主塔約 2.2 km。主塔位於 WNW (約 286°)，" +
            "**5 月底與 7 月底兩個對齊窗口**（接近夏至高方位）。",
    )

    val RIVERSIDE_PARK_2 = Hotspot(
        id = "riverside_park_2",
        nameRes = R.string.hotspot_riverside_park_2,
        customName = null,
        position = GeoPoint(25.168501, 121.441928, elevationMeters = 22.8),
        description = "水岸公園 2，距主塔約 2.5 km。主塔位於 WNW (~287°)，" +
            "**夏至前後**高方位對齊窗口。",
    )

    val RIVERSIDE_PARK_1 = Hotspot(
        id = "riverside_park_1",
        nameRes = R.string.hotspot_riverside_park_1,
        customName = null,
        position = GeoPoint(25.167795, 121.442745, elevationMeters = 14.166),
        description = "水岸公園 1，距主塔約 2.7 km。主塔位於 WNW (~288°)，" +
            "**夏至前後**太陽接近此方位時最易拍到夕陽穿塔。",
    )

    val TAMSUI_MRT = Hotspot(
        id = "tamsui_mrt",
        nameRes = R.string.hotspot_tamsui_mrt,
        customName = null,
        position = GeoPoint(25.167099, 121.444855, elevationMeters = 21.8),
        description = "淡水捷運站河岸，距主塔約 2.9 km。主塔位於 WNW (~288°)，" +
            "**夏至前後**最接近，交通便利、適合臨時前往。",
    )

    // ============== 南岸 (八里側) — 由西到東 ==============

    val BALI = Hotspot(
        id = "bali",
        nameRes = R.string.hotspot_bali,
        customName = null,
        position = GeoPoint(25.161664183921225, 121.42931778723609, elevationMeters = 3.0),
        description = "八里左岸公園，距主塔約 1.9 km。主塔位於 NW (~323°)，超出太陽方位範圍 25°；" +
            "**夏至前後**太陽達 298° 時最接近，廣角可同框拍主塔加夕陽。",
    )

    val BALI_FERRY = Hotspot(
        id = "bali_ferry",
        nameRes = R.string.hotspot_bali_ferry,
        customName = null,
        position = GeoPoint(25.161529542702116, 121.43176695471796, elevationMeters = 3.0),
        description = "八里渡船頭，距主塔約 2.1 km。主塔位於 NW (~317°)，超出太陽方位範圍 19°；" +
            "**夏至前後**最接近。對岸淡水老街天際線可入鏡。",
    )

    val ALL: List<Hotspot> = listOf(
        // 北岸 (淡水) — 由西到東
        STARBUCKS,
        CUSTOMS_WHARF,
        MACKAY_LANDING,
        TAMSUI_FERRY,
        RIVERSIDE_PARK_2,
        RIVERSIDE_PARK_1,
        TAMSUI_MRT,
        // 南岸 (八里) — 由西到東
        BALI,
        BALI_FERRY,
    )

    fun findById(id: String): Hotspot? = ALL.firstOrNull { it.id == id }
}
