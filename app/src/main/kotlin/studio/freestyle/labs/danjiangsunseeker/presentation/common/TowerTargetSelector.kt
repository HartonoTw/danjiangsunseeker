package studio.freestyle.labs.danjiangsunseeker.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
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

@Composable
fun TowerTargetSelector(
    selected: TowerTarget,
    onSelect: (TowerTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            TowerTarget.entries.forEach { target ->
                val isSelected = selected == target
                Text(
                    text = towerTargetLabel(target),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { onSelect(target) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
