package studio.freestyle.labs.danjiangsunseeker.presentation.calendar

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.GoldenDate
import studio.freestyle.labs.danjiangsunseeker.presentation.common.TowerTargetSelector
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoldenCalendarScreen(
    onGoToSimulator: (hotspotId: String, date: LocalDate, towerTarget: TowerTarget) -> Unit = { _, _, _ -> },
    vm: GoldenCalendarViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("365 天黃金日曆", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "未來一年內，太陽日落方位與主塔方位接近對齊的日子",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        TowerTargetSelector(
            selected = state.towerTarget,
            onSelect = vm::setTowerTarget,
        )
        Spacer(Modifier.height(8.dp))

        // FlowRow：大字體下空間不足會自動換到下一行，不會被裁切
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(0.5, 1.0, 2.0, 5.0).forEach { tol ->
                FilterChip(
                    selected = state.toleranceDegrees == tol,
                    onClick = { vm.setTolerance(tol) },
                    // 整數值 (1/2/5) 拿掉 .0 後綴，省點寬度
                    label = {
                        val labelText = if (tol == tol.toInt().toDouble()) "±${tol.toInt()}°"
                                        else "±${"%.1f".format(tol)}°"
                        Text(labelText)
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (state.dates.isEmpty()) {
            Text(
                "在 ±${state.toleranceDegrees}° 容差下，未來一年沒有完美對齊的日子；試試放寬到 ±5°。",
                color = MaterialTheme.colorScheme.outline,
            )
            return@Column
        }

        Text(
            "共找到 ${state.dates.size} 個機會",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.dates, key = { "${it.date}-${it.hotspot.id}" }) { golden ->
                GoldenDateRow(
                    golden,
                    onAddToCalendar = {
                        runCatching {
                            ctx.startActivity(AddToCalendarHelper.buildIntent(ctx, golden))
                        }.onFailure {
                            Toast.makeText(ctx, "找不到行事曆 App", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onGoToSimulator = {
                        onGoToSimulator(golden.hotspot.id, golden.date, golden.towerTarget)
                    },
                )
            }
        }
    }
}

@Composable
private fun GoldenDateRow(
    golden: GoldenDate,
    onAddToCalendar: () -> Unit,
    onGoToSimulator: () -> Unit = {},
) {
    val name = golden.hotspot.nameRes?.let { stringResource(it) }
        ?: golden.hotspot.customName.orEmpty()
    // 點整列 → 帶日期 + 地點 + 塔頂/塔基跳到焦距模擬；「加入」按鈕獨立消費不觸發跳轉
    Card(
        onClick = onGoToSimulator,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    golden.date.format(DATE_FMT),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${golden.towerTarget.displayName} ${golden.sunsetTime?.toLocalTime()?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "—"}" +
                        " · 偏差 ${"%+.2f".format(golden.alignmentOffsetDegrees)}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            TextButton(onClick = onAddToCalendar) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("加入")
            }
        }
    }
}

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E)", Locale.TAIWAN)
