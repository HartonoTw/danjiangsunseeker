package studio.freestyle.labs.danjiangsunseeker.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.FocalAdvice
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.VerdictLevel

/** TowerTarget 的本地化顯示字串（塔頂 / 塔基）。 */
@Composable
fun towerTargetLabel(target: TowerTarget): String = stringResource(
    when (target) {
        TowerTarget.UpperY -> R.string.tower_target_upper
        TowerTarget.LowerY -> R.string.tower_target_lower
    },
)

/** 拍攝品質評語的本地化字串。 */
@Composable
fun verdictLabel(level: VerdictLevel): String = stringResource(
    when (level) {
        VerdictLevel.TOP -> R.string.verdict_top
        VerdictLevel.GOOD -> R.string.verdict_good
        VerdictLevel.MEDIUM -> R.string.verdict_medium
        VerdictLevel.FAIR -> R.string.verdict_fair
        VerdictLevel.POOR -> R.string.verdict_poor
    },
)

/** 焦段構圖建議的本地化字串。 */
@Composable
fun focalAdviceLabel(advice: FocalAdvice): String = stringResource(
    when (advice) {
        FocalAdvice.TOO_WIDE -> R.string.focal_advice_too_wide
        FocalAdvice.TOO_TELE -> R.string.focal_advice_too_tele
        FocalAdvice.OK -> R.string.focal_advice_ok
    },
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TowerTargetSelector(
    selected: TowerTarget,
    onSelect: (TowerTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TowerTarget.entries.forEach { target ->
            FilterChip(
                selected = selected == target,
                onClick = { onSelect(target) },
                label = { Text(towerTargetLabel(target)) },
            )
        }
    }
}
