package studio.freestyle.labs.danjiangsunseeker.domain.model

enum class TowerTarget(
    val elevationMeters: Double,
) {
    UpperY(BridgeTower.TOWER_TIP_ELEVATION_M),
    LowerY(BridgeTower.BASE_ELEVATION_M),
}
