package studio.freestyle.labs.danjiangsunseeker.presentation.calendar

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import studio.freestyle.labs.danjiangsunseeker.domain.model.TideKind
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.GoldenDate
import studio.freestyle.labs.danjiangsunseeker.presentation.common.MoonPhaseIcon
import studio.freestyle.labs.danjiangsunseeker.presentation.common.TowerTargetSelector
import studio.freestyle.labs.danjiangsunseeker.presentation.common.towerTargetLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoldenCalendarScreen(
    onGoToSimulator: (hotspotId: String, date: LocalDate, towerTarget: TowerTarget, useMoon: Boolean) -> Unit = { _, _, _, _ -> },
    vm: GoldenCalendarViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .padding(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(stringResource(R.string.calendar_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        if (state.mode == CalendarMode.MOON) R.string.calendar_moon_subtitle
                        else R.string.calendar_subtitle,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // 塔頂/塔基 與 夕陽日/月亮日 同一列（月亮模式為付費功能；鎖定時不顯示切換）
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 各元件高度不同 (塔頂/塔基 32dp、FilterChip 含 48dp 觸控區)，逐一垂直置中對齊
            TowerTargetSelector(
                selected = state.towerTarget,
                onSelect = vm::setTowerTarget,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            if (state.premiumUnlocked) {
                FilterChip(
                    selected = state.mode == CalendarMode.SUN,
                    onClick = { vm.setMode(CalendarMode.SUN) },
                    label = { Text(stringResource(R.string.calendar_mode_sun)) },
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                FilterChip(
                    selected = state.mode == CalendarMode.MOON,
                    onClick = { vm.setMode(CalendarMode.MOON) },
                    label = { Text(stringResource(R.string.calendar_mode_moon)) },
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
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
                stringResource(R.string.calendar_none_found, state.toleranceDegrees.toString()),
                color = MaterialTheme.colorScheme.outline,
            )
            return@Column
        }

        Text(
            stringResource(R.string.calendar_found_count, state.dates.size),
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
                            Toast.makeText(ctx, ctx.getString(R.string.toast_no_calendar_app), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onGoToSimulator = {
                        onGoToSimulator(golden.hotspot.id, golden.date, golden.towerTarget, golden.isMoon)
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 第一行：日期 + 熱點名稱（省垂直空間）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        golden.date.format(DATE_FMT),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                // 第二行（月亮模式）：月相圖案 + 亮面百分比
                if (golden.isMoon && golden.moonFractionLit != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoonPhaseIcon(
                            fractionLit = golden.moonFractionLit.toFloat(),
                            waxing = golden.moonWaxing,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.moon_lit, "%.0f".format(golden.moonFractionLit * 100)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(
                        R.string.calendar_row_detail,
                        towerTargetLabel(golden.towerTarget),
                        golden.sunsetTime?.toLocalTime()?.let { "%02d:%02d".format(it.hour, it.minute) } ?: stringResource(R.string.value_none),
                        "%+.2f".format(golden.alignmentOffsetDegrees),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                // 潮汐標註 (付費功能；鎖定時 tideInfo 為 null)
                val tide = golden.tideInfo
                if (tide != null && tide.extremes.isNotEmpty()) {
                    val highLabel = stringResource(R.string.tide_high)
                    val lowLabel = stringResource(R.string.tide_low)
                    val tideLine = tide.extremes.joinToString("  ") { ex ->
                        (if (ex.kind == TideKind.HIGH) highLabel else lowLabel) +
                            " %02d:%02d".format(ex.time.hour, ex.time.minute)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tideLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            TextButton(onClick = onAddToCalendar) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text(stringResource(R.string.calendar_add))
            }
        }
    }
}

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E)", Locale.getDefault())
