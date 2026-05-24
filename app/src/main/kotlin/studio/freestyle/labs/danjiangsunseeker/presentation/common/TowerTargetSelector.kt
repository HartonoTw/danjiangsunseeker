package studio.freestyle.labs.danjiangsunseeker.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget

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
                label = { Text(target.displayName) },
            )
        }
    }
}
