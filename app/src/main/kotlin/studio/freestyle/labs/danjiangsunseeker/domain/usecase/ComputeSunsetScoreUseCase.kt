package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import javax.inject.Inject
import kotlin.math.abs

/**
 * 計算「淡江夕照」拍攝品質分數 (0..100)。
 *
 * 僅以太陽對齊主塔的方位偏差計分；天氣資料（CWA）已移除。
 *
 * 分數對應（曲線拉平：±20° 仍給 60 分，承認廣角同框也算可拍）：
 *   0°    → 100  完美對齊（穿塔）
 *   ±2°   →  88  良好以上
 *   ±10°  →  70  良好下限
 *   ±20°  →  60  中等門檻 — 廣角可同框構圖
 *   ±30°  →  40  普通
 *   ±60°  →  10  不建議
 *   ±90°+ →   0
 */
class ComputeSunsetScoreUseCase @Inject constructor() {

    operator fun invoke(alignmentOffsetDegrees: Double?): SunsetScore {
        val alignment = alignmentOffsetDegrees?.let { alignmentSubScore(it) } ?: 0.0
        return SunsetScore(
            overall   = alignment,
            alignment = alignment,
            verdict   = verdictFor(alignment),
        )
    }

    /**
     * 對齊角度 → 子分數 (100 分制)。分段線性，銜接點處連續。
     */
    private fun alignmentSubScore(offsetDeg: Double): Double {
        val a = abs(offsetDeg)
        return when {
            a <= 0.5  -> 100.0 - a * 8.0
            a <= 2.0  ->  96.0 - (a - 0.5)  * 5.333
            a <= 10.0 ->  88.0 - (a - 2.0)  * 2.25
            a <= 20.0 ->  70.0 - (a - 10.0) * 1.0
            a <= 30.0 ->  60.0 - (a - 20.0) * 2.0
            a <= 60.0 ->  40.0 - (a - 30.0) * 1.0
            a <= 90.0 ->  10.0 - (a - 60.0) * 0.333
            else      ->   0.0
        }.coerceIn(0.0, 100.0)
    }

    private fun verdictFor(score: Double): String = when {
        score >= 85 -> "頂級拍攝日"
        score >= 70 -> "良好"
        score >= 50 -> "中等"
        score >= 30 -> "普通"
        else        -> "不建議"
    }
}

data class SunsetScore(
    val overall   : Double,
    val alignment : Double,
    val verdict   : String,
)
