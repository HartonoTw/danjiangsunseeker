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
        descriptionRes = R.string.hotspot_desc_starbucks,
    )

    val CUSTOMS_WHARF = Hotspot(
        id = "customs_wharf",
        nameRes = R.string.hotspot_customs_wharf,
        customName = null,
        position = GeoPoint(25.174439762352144, 121.43177818353163, elevationMeters = 3.0),
        descriptionRes = R.string.hotspot_desc_customs_wharf,
    )

    val MACKAY_LANDING = Hotspot(
        id = "mackay_landing",
        nameRes = R.string.hotspot_mackay_landing,
        customName = null,
        position = GeoPoint(25.171056, 121.437405, elevationMeters = 20.6),
        descriptionRes = R.string.hotspot_desc_mackay_landing,
    )

    val TAMSUI_FERRY = Hotspot(
        id = "tamsui_ferry",
        nameRes = R.string.hotspot_tamsui_ferry,
        customName = null,
        position = GeoPoint(25.17000726568226, 121.43877415755742, elevationMeters = 3.0),
        descriptionRes = R.string.hotspot_desc_tamsui_ferry,
    )

    val RIVERSIDE_PARK_2 = Hotspot(
        id = "riverside_park_2",
        nameRes = R.string.hotspot_riverside_park_2,
        customName = null,
        position = GeoPoint(25.168501, 121.441928, elevationMeters = 22.8),
        descriptionRes = R.string.hotspot_desc_riverside_park_2,
    )

    val RIVERSIDE_PARK_1 = Hotspot(
        id = "riverside_park_1",
        nameRes = R.string.hotspot_riverside_park_1,
        customName = null,
        position = GeoPoint(25.167795, 121.442745, elevationMeters = 14.166),
        descriptionRes = R.string.hotspot_desc_riverside_park_1,
    )

    val TAMSUI_MRT = Hotspot(
        id = "tamsui_mrt",
        nameRes = R.string.hotspot_tamsui_mrt,
        customName = null,
        position = GeoPoint(25.167099, 121.444855, elevationMeters = 21.8),
        descriptionRes = R.string.hotspot_desc_tamsui_mrt,
    )

    // ============== 南岸 (八里側) — 由西到東 ==============

    val BALI = Hotspot(
        id = "bali",
        nameRes = R.string.hotspot_bali,
        customName = null,
        position = GeoPoint(25.161664183921225, 121.42931778723609, elevationMeters = 3.0),
        descriptionRes = R.string.hotspot_desc_bali,
    )

    val BALI_FERRY = Hotspot(
        id = "bali_ferry",
        nameRes = R.string.hotspot_bali_ferry,
        customName = null,
        position = GeoPoint(25.161529542702116, 121.43176695471796, elevationMeters = 3.0),
        descriptionRes = R.string.hotspot_desc_bali_ferry,
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
