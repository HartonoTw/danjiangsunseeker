package studio.freestyle.labs.danjiangsunseeker.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.model.LunarPhase
import kotlin.math.abs

/** 月相的本地化顯示字串（朔 / 眉月 / 上弦 …）。 */
@Composable
fun lunarPhaseLabel(phase: LunarPhase): String = stringResource(
    when (phase) {
        LunarPhase.NEW -> R.string.moon_phase_new
        LunarPhase.WAXING_CRESCENT -> R.string.moon_phase_waxing_crescent
        LunarPhase.FIRST_QUARTER -> R.string.moon_phase_first_quarter
        LunarPhase.WAXING_GIBBOUS -> R.string.moon_phase_waxing_gibbous
        LunarPhase.FULL -> R.string.moon_phase_full
        LunarPhase.WANING_GIBBOUS -> R.string.moon_phase_waning_gibbous
        LunarPhase.LAST_QUARTER -> R.string.moon_phase_last_quarter
        LunarPhase.WANING_CRESCENT -> R.string.moon_phase_waning_crescent
    },
)

/**
 * 程式繪製的月相圖案 (Canvas)：依亮面比例 [fractionLit] 與盈虧 [waxing] 畫出明暗界線。
 *
 * 自然產生：滿月 (全亮)、新月 (全暗)、**上弦 (右半亮)**、**下弦 (左半亮)**、盈/虧凸月與眉月。
 * 北半球視角：盈月亮面在右、虧月亮面在左。
 *
 * 繪法：
 *  1. 先畫整顆暗面圓。
 *  2. 裁切到圓內後，在亮側畫半圓 (亮面極限)。
 *  3. 以一條水平半徑為 r·|1−2f| 的橢圓修正明暗界線：
 *       - 眉月 (f<0.5)：橢圓填暗色 → 由半圓內側挖出，剩外緣細弦月。
 *       - 凸月 (f>0.5)：橢圓填亮色 → 由半圓向暗側擴張。
 */
@Composable
fun MoonPhaseIcon(
    fractionLit: Float,
    waxing: Boolean,
    modifier: Modifier = Modifier,
    litColor: Color = Color(0xFFF4E8C1),
    darkColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    outlineColor: Color = MaterialTheme.colorScheme.outline,
) {
    val f = fractionLit.coerceIn(0f, 1f)
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val circleRect = Rect(cx - r, cy - r, cx + r, cy + r)

        // 1) 暗面整圓
        drawCircle(color = darkColor, radius = r, center = Offset(cx, cy))

        // 2) + 3) 亮面 (裁切於圓內)
        val circlePath = Path().apply { addOval(circleRect) }
        clipPath(circlePath) {
            // 亮側半圓：盈月在右 (起始 -90°)、虧月在左 (起始 90°)，掃 180°
            val litStart = if (waxing) -90f else 90f
            drawArc(
                color = litColor,
                startAngle = litStart,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(cx - r, cy - r),
                size = Size(2 * r, 2 * r),
            )
            // 明暗界線橢圓
            val a = r * abs(1f - 2f * f)
            val terminatorColor = if (f < 0.5f) darkColor else litColor
            drawOval(
                color = terminatorColor,
                topLeft = Offset(cx - a, cy - r),
                size = Size(2 * a, 2 * r),
            )
        }

        // 邊框 (讓新月仍可見輪廓)
        drawCircle(
            color = outlineColor,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = r * 0.06f),
        )
    }
}
