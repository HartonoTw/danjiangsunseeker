package studio.freestyle.labs.danjiangsunseeker.domain.model

enum class TowerTarget(
    val displayName: String,
    val elevationMeters: Double,
) {
    UpperY("塔頂", BridgeTower.TOWER_TIP_ELEVATION_M),
    LowerY("塔基", BridgeTower.BASE_ELEVATION_M),
}
