package studio.freestyle.labs.danjiangsunseeker.presentation.hotspot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.SunTrailPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.TideKind
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.presentation.common.MoonPhaseIcon
import studio.freestyle.labs.danjiangsunseeker.presentation.common.lunarPhaseLabel
import studio.freestyle.labs.danjiangsunseeker.presentation.map.ComposeMapLibre
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.AlignmentClass
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.SunsetScore
import studio.freestyle.labs.danjiangsunseeker.presentation.common.TowerTargetSelector
import studio.freestyle.labs.danjiangsunseeker.presentation.common.towerTargetLabel
import studio.freestyle.labs.danjiangsunseeker.presentation.common.verdictLabel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HotspotListScreen(
    onHotspotClick: (String) -> Unit,
    onGoToSimulator: (hotspotId: String, date: LocalDate, towerTarget: TowerTarget, useMoon: Boolean) -> Unit = { _, _, _, _ -> },
    vm: HotspotListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now(ZoneId.of("Asia/Taipei")) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val json = vm.exportJson()
                writeUriText(ctx, it, json)
                Toast.makeText(ctx, ctx.getString(R.string.toast_export_done), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            val text = readUriText(ctx, it)
            if (text != null) vm.importJson(text)
        }
    }
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.flyEditorToCurrentLocation()
        else Toast.makeText(ctx, ctx.getString(R.string.toast_location_permission_denied), Toast.LENGTH_SHORT).show()
    }

    state.importMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            vm.clearImportMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            HotspotHeader(
                title = headerLabel(state.date, today),
                count = state.predictions.size,
                onImport = { importLauncher.launch(arrayOf("application/json")) },
                onExport = {
                    val fname = "hotspots_${today.format(DateTimeFormatter.ofPattern("yyyyMMdd"))}.json"
                    exportLauncher.launch(fname)
                },
                onAdd = { vm.showEditor() },
            )
            Spacer(Modifier.height(12.dp))
            DateChipRow(
                selectedDate = state.date,
                today = today,
                onPickQuick = { vm.loadFor(it) },
                onOpenPicker = { showDatePicker = true },
                selectedTowerTarget = state.towerTarget,
                onSelectTowerTarget = vm::setTowerTarget,
                premiumUnlocked = state.premiumUnlocked,
                body = state.body,
                onSelectBody = vm::setBody,
            )
            Spacer(Modifier.height(8.dp))

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.predictions, key = { it.prediction.hotspot.id }) { p ->
                        HotspotRow(
                            p,
                            isMoon = state.body == HotspotBody.MOON,
                            onClick = { onHotspotClick(p.prediction.hotspot.id) },
                            onEdit = { vm.showEditor(p.prediction.hotspot) },
                            onNavigate = {
                                openGoogleMaps(
                                    ctx,
                                    p.prediction.hotspot.position.latitude,
                                    p.prediction.hotspot.position.longitude,
                                    p.prediction.hotspot.nameRes?.let { ctx.getString(it) }
                                        ?: p.prediction.hotspot.customName.orEmpty(),
                                )
                            },
                            onGoToSimulator = {
                                onGoToSimulator(
                                    p.prediction.hotspot.id,
                                    state.date,
                                    state.towerTarget,
                                    state.body == HotspotBody.MOON,
                                )
                            },
                        )
                    }
                }
            }
        }

    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                        vm.loadFor(date)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }

    state.editor?.let { ed ->
        ed.locationMessage?.let { msg ->
            LaunchedEffect(msg) {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                vm.clearEditorLocationMessage()
            }
        }
        if (ed.showLocationPicker) {
            // ── 地圖選點覆蓋層（全螢幕）────────────────────────────────
            LocationPickerOverlay(
                initLat = ed.latitude.toDoubleOrNull() ?: BridgeTower.LATITUDE,
                initLon = ed.longitude.toDoubleOrNull() ?: BridgeTower.LONGITUDE,
                markInitialLocation = ed.isEditing,
                currentLat = ed.latitude.toDoubleOrNull(),
                currentLon = ed.longitude.toDoubleOrNull(),
                currentLocationFlyRequest = ed.currentLocationFlyRequest,
                locatingCurrentLocation = ed.locatingCurrentLocation,
                onFlyToCurrentLocation = {
                    val granted = ContextCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) vm.flyEditorToCurrentLocation()
                    else locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                onConfirm = { lat, lon ->
                    vm.updateEditorField(EditorField.LAT, "%.6f".format(lat))
                    vm.updateEditorField(EditorField.LON, "%.6f".format(lon))
                    vm.closeLocationPicker()
                },
                onDismiss = { vm.closeLocationPicker() },
            )
        } else {
            HotspotEditorDialog(
                state = ed,
                onChange = vm::updateEditorField,
                onSave = { vm.saveEditor() },
                onDelete = { vm.deleteFromEditor() },
                onDismiss = { vm.closeEditor() },
                onOpenPicker = { vm.openLocationPicker() },
            )
        }
    }
}

@Composable
private fun HotspotHeader(
    title: String,
    count: Int,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onAdd: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$count 個拍攝點",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HeaderActionButton(Icons.Outlined.FolderOpen, stringResource(R.string.cd_import), onImport)
            HeaderActionButton(Icons.Outlined.Upload, stringResource(R.string.cd_export), onExport)
            HeaderActionButton(Icons.Outlined.Add, stringResource(R.string.hotspot_add_title), onAdd)
        }
    }
}

@Composable
private fun HeaderActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.padding(start = 6.dp).size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun headerLabel(selectedDate: LocalDate, today: LocalDate): String {
    val diff = selectedDate.toEpochDay() - today.toEpochDay()
    val prefix = when (diff) {
        0L -> stringResource(R.string.header_today)
        1L -> stringResource(R.string.header_tomorrow)
        2L -> stringResource(R.string.header_day_after)
        in 3L..30L -> stringResource(R.string.header_in_days, diff.toInt())
        in -7L..-1L -> stringResource(R.string.header_days_ago, (-diff).toInt())
        else -> ""
    }
    val dateText = selectedDate.format(DateTimeFormatter.ofPattern("M/d (E)", Locale.getDefault()))
    return if (prefix.isNotEmpty()) "$prefix · $dateText" else dateText
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DateChipRow(
    selectedDate: LocalDate,
    today: LocalDate,
    onPickQuick: (LocalDate) -> Unit,
    onOpenPicker: () -> Unit,
    selectedTowerTarget: TowerTarget,
    onSelectTowerTarget: (TowerTarget) -> Unit,
    premiumUnlocked: Boolean,
    body: HotspotBody,
    onSelectBody: (HotspotBody) -> Unit,
) {
    val quickDates = listOf(
        stringResource(R.string.date_today) to today,
        stringResource(R.string.date_tomorrow) to today.plusDays(1),
        stringResource(R.string.date_day_after) to today.plusDays(2),
        stringResource(R.string.date_weekend) to nextSaturday(today),
    )
    // FlowRow：大字體空間不足時 chip 自動換行（跟「日曆」page 的容差 chip 一致），
    //   不再用 horizontalScroll 強迫使用者手動橫滑
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        quickDates.forEach { (label, date) ->
            FilterChip(
                selected = selectedDate == date,
                onClick = { onPickQuick(date) },
                label = { Text(label) },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 日期選擇：只留行事曆圖示（不再顯示日期文字），騰出空間給「太陽/月亮」切換。
            //   完整日期仍顯示在上方標題列 (headerLabel)。
            Surface(
                onClick = onOpenPicker,
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TowerTargetSelector(
                selected = selectedTowerTarget,
                onSelect = onSelectTowerTarget,
            )
            // 太陽 / 月亮切換（付費功能；鎖定時不顯示）— 月亮模式改以「月亮升起穿塔」為對齊基準。
            if (premiumUnlocked) {
                FilterChip(
                    selected = body == HotspotBody.SUN,
                    onClick = { onSelectBody(HotspotBody.SUN) },
                    label = { Text(stringResource(R.string.focal_body_sun)) },
                )
                FilterChip(
                    selected = body == HotspotBody.MOON,
                    onClick = { onSelectBody(HotspotBody.MOON) },
                    label = { Text(stringResource(R.string.focal_body_moon)) },
                )
            }
        }
    }
}

private fun nextSaturday(from: LocalDate): LocalDate {
    var d = from
    while (d.dayOfWeek != java.time.DayOfWeek.SATURDAY) d = d.plusDays(1)
    return d
}

private val ROW_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun HotspotRow(
    p: ScoredPrediction,
    isMoon: Boolean = false,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onNavigate: () -> Unit,
    onGoToSimulator: () -> Unit = {},
) {
    val name = p.prediction.hotspot.nameRes?.let { stringResource(it) }
        ?: p.prediction.hotspot.customName.orEmpty()
    val targetTime = p.prediction.targetTime?.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    val offset = p.prediction.alignmentOffsetDegrees
    val isOverride = p.isOverride
    val isPureCustom = p.prediction.hotspot.isCustom && p.prediction.hotspot.id.startsWith("custom_")
    val isTooFar = p.prediction.classification == AlignmentClass.TOO_FAR

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isTooFar) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        Row(
            // Top 對齊：當 Column 內容因大字體而變高時，Badge 與按鈕靠頂，不會被強制拉伸
            verticalAlignment = Alignment.Top,
        ) {
            if (isTooFar) TooFarBadge() else ScoreBadge(p.score, scoreBadgeColor(p.score.overall))
            Spacer(Modifier.width(10.dp))

            // ── 日落前一小時太陽軌跡縮圖（TOO_FAR 或無軌跡時顯示空白佔位）
            // 點縮圖 → 帶日期 + 地點 + 塔頂/塔基跳到焦距模擬頁
            HotspotThumbnail(
                trail = p.prediction.lastHourSunTrail,
                towerBearing = p.prediction.bearingToTowerDegrees,
                distanceToTowerMeters = p.prediction.distanceToTowerMeters,
                classification = p.prediction.classification,
                isMoon = isMoon,
                modifier = Modifier
                    .width(86.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onGoToSimulator),
            )
            Spacer(Modifier.width(12.dp))

            // ── 主要內容：全部垂直堆疊，任何字體大小都不會橫向溢出 ──────
            Column(modifier = Modifier.weight(1f)) {
                // 名稱 — 自動換行，不放在 Row 內避免溢出
                Text(
                    text = name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 自訂 / 已修改 標籤放在名稱下方獨立一行
                if (isPureCustom) {
                    Text(
                        stringResource(R.string.hotspot_tag_custom),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else if (isOverride) {
                    Text(
                        stringResource(R.string.hotspot_tag_modified),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                Spacer(Modifier.height(4.dp))

                if (isTooFar) {
                    Text(
                        stringResource(R.string.hotspot_too_far, (p.prediction.distanceToTowerMeters / 1000.0).format(1)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    // 日落時間與偏差各佔一行，避免放在同一 Row 時大字體溢出
                    Text(
                        stringResource(R.string.hotspot_target_time, towerTargetLabel(p.prediction.towerTarget), targetTime ?: stringResource(R.string.value_none)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    offset?.let {
                        Text(
                            stringResource(R.string.hotspot_alignment_offset, "%+.2f".format(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(
                        stringResource(
                            R.string.hotspot_distance_verdict,
                            (p.prediction.distanceToTowerMeters / 1000.0).format(2),
                            verdictLabel(p.score.verdict),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // ── 操作按鈕：垂直排列，大字體時與高 Column 並列不擠版 ────
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.cd_edit), modifier = Modifier.size(19.dp))
                    }
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    IconButton(onClick = onNavigate, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.Directions,
                            contentDescription = stringResource(R.string.cd_navigate),
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // ── 月相・潮汐 (付費功能；moonInfo/tideInfo 鎖定時為 null)：放在分數＋簡圖下方，整列寬 ──
        val moon = p.prediction.moonInfo
        val tide = p.prediction.tideInfo
        if (moon != null || (tide != null && tide.extremes.isNotEmpty())) {
            Spacer(Modifier.height(8.dp))
            if (moon != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MoonPhaseIcon(
                        fractionLit = moon.fractionLit.toFloat(),
                        waxing = moon.waxing,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(
                            R.string.moon_summary,
                            lunarPhaseLabel(moon.phase),
                            "%.0f".format(moon.fractionLit * 100),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (tide != null && tide.extremes.isNotEmpty()) {
                val highLabel = stringResource(R.string.tide_high)
                val lowLabel = stringResource(R.string.tide_low)
                val tideLine = tide.extremes.joinToString("  ") { ex ->
                    (if (ex.kind == TideKind.HIGH) highLabel else lowLabel) +
                        " " + ex.time.format(ROW_HM)
                }
                Text(
                    tideLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
      }
    }
}

@Composable
private fun ScoreBadge(score: SunsetScore, color: Color) {
    Box(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            score.overall.toInt().toString(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TooFarBadge() {
    Box(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.outline),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.badge_too_far), color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun scoreBadgeColor(score: Double): Color = when {
    score >= 85 -> MaterialTheme.colorScheme.primary
    score >= 70 -> MaterialTheme.colorScheme.secondary
    score >= 50 -> MaterialTheme.colorScheme.tertiary
    score >= 30 -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun HotspotEditorDialog(
    state: HotspotEditorState,
    onChange: (EditorField, String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (state.isEditing) stringResource(R.string.hotspot_edit_title) else stringResource(R.string.hotspot_add_title),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onChange(EditorField.NAME, it) },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.latitude,
                        onValueChange = { onChange(EditorField.LAT, it) },
                        label = { Text(stringResource(R.string.field_latitude)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.longitude,
                        onValueChange = { onChange(EditorField.LON, it) },
                        label = { Text(stringResource(R.string.field_longitude)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                // ── 地圖選點按鈕 ─────────────────────────────────────────
                OutlinedButton(
                    onClick = onOpenPicker,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.editor_pick_on_map))
                }
                OutlinedTextField(
                    value = state.elevation,
                    onValueChange = { onChange(EditorField.ELEV, it) },
                    label = { Text(stringResource(R.string.field_elevation)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { onChange(EditorField.DESC, it) },
                    label = { Text(stringResource(R.string.field_description_optional)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.originalIsDefault) {
                    Text(
                        stringResource(R.string.editor_default_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else {
                    Text(
                        stringResource(R.string.editor_tip),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            Row {
                if (state.canDelete) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(4.dp))
                        Text(
                            if (state.canResetToDefault) stringResource(R.string.action_reset) else stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

private fun writeUriText(ctx: Context, uri: Uri, text: String) {
    ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
}

private fun readUriText(ctx: Context, uri: Uri): String? =
    ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }

private fun Double.format(digits: Int) = "%.${digits}f".format(this)

/**
 * 全螢幕 MapLibre 地圖選點覆蓋層。
 * 使用者點擊地圖選點，確認後回傳座標；預設中心為目前編輯值（或主塔位置）。
 */
@Composable
private fun LocationPickerOverlay(
    initLat: Double,
    initLon: Double,
    markInitialLocation: Boolean,
    currentLat: Double?,
    currentLon: Double?,
    currentLocationFlyRequest: Int,
    locatingCurrentLocation: Boolean,
    onFlyToCurrentLocation: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    // 採「地圖中心」選點：座標永遠等於相機中心（中央準星所指），初始為傳入值。
    var pickedLat by remember { mutableStateOf<Double?>(initLat) }
    var pickedLon by remember { mutableStateOf<Double?>(initLon) }
    var mapRef    by remember { mutableStateOf<MapLibreMap?>(null) }

    LaunchedEffect(currentLocationFlyRequest) {
        if (currentLocationFlyRequest == 0) return@LaunchedEffect
        val lat = currentLat ?: return@LaunchedEffect
        val lon = currentLon ?: return@LaunchedEffect
        pickedLat = lat
        pickedLon = lon
        mapRef?.animateCamera(
            org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                LatLng(lat, lon),
                mapRef?.cameraPosition?.zoom?.coerceAtLeast(16.0) ?: 16.0,
            ),
            900,
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ── 地圖 ────────────────────────────────────────────────
                ComposeMapLibre(
                    modifier = Modifier.fillMaxSize(),
                    onMapReady = { map, _ ->
                        mapRef = map
                        map.moveCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                LatLng(initLat, initLon), 14.0,
                            ),
                        )
                        // 相機停止移動時，把「地圖中心」更新為目前選點座標。
                        map.addOnCameraIdleListener {
                            val center = map.cameraPosition.target ?: return@addOnCameraIdleListener
                            pickedLat = center.latitude
                            pickedLon = center.longitude
                        }
                    },
                    onMapClick = { lat, lon ->
                        // 點擊＝把地圖飛到該點，中心準星隨即對準（座標由 idle 監聽更新）
                        mapRef?.animateCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newLatLng(LatLng(lat, lon)),
                            300,
                        )
                    },
                )

                // ── 頂部提示條 ──────────────────────────────────────────
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.picker_tap_to_select), style = MaterialTheme.typography.titleSmall)
                            if (pickedLat != null) {
                                Text(
                                    "📍 ${"%.6f".format(pickedLat)}, ${"%.6f".format(pickedLon)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.picker_not_selected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val center = mapRef?.cameraPosition?.target ?: return@Button
                                onConfirm(center.latitude, center.longitude)
                            },
                            enabled = mapRef != null,
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.action_confirm))
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 82.dp, end = 16.dp)
                        .size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                ) {
                    IconButton(
                        onClick = onFlyToCurrentLocation,
                        enabled = !locatingCurrentLocation,
                    ) {
                        if (locatingCurrentLocation) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.MyLocation, contentDescription = stringResource(R.string.cd_fly_to_current_location))
                        }
                    }
                }

                // ── 中心準星（選點依據：存檔即採此中心點座標）──────────
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  日落前一小時太陽軌跡縮圖
// ════════════════════════════════════════════════════════════════════════

/**
 * 縮圖：顯示日落前 60min 到日落瞬間的太陽軌跡 + 主塔方位參考線。
 *
 * 視窗中心置於「塔方位 ↔ 日落方位」中點；水平 FOV 60°（夠寬可容納大部分對齊情境）。
 * 若資料不齊（TOO_FAR / 無日落）則畫一個夜空 placeholder。
 */
@Composable
private fun HotspotThumbnail(
    trail: List<SunTrailPoint>,
    towerBearing: Double,
    distanceToTowerMeters: Double,
    classification: AlignmentClass,
    isMoon: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (trail.isEmpty()) {
            // Placeholder：夜空 + 一條地平線
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF1A1226), Color(0xFF0A0814)),
                ),
                size = size,
            )
            val horizonY = size.height * 0.78f
            drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, horizonY), Offset(size.width, horizonY), 1f)
            return@Canvas
        }
        drawSunTrailThumbnail(trail, towerBearing, distanceToTowerMeters, classification, isMoon)
    }
}

private fun DrawScope.drawSunTrailThumbnail(
    trail: List<SunTrailPoint>,
    towerBearing: Double,
    distanceToTowerMeters: Double,
    classification: AlignmentClass,
    isMoon: Boolean,
) {
    val w = size.width
    val h = size.height

    // 1. 天空漸層：太陽＝日落暖色；月亮＝夜空冷色
    val skyColors = if (isMoon) {
        listOf(
            Color(0xFF13203F),       // 夜藍
            Color(0xFF1A2A4A),       // 暗藍
            Color(0xFF070512),       // 近黑
        )
    } else {
        listOf(
            Color(0xFFC0392B),       // 紅
            Color(0xFFFF8C42),       // 橙
            Color(0xFF1A0A3A),       // 深藍紫
        )
    }
    drawRect(
        brush = Brush.verticalGradient(skyColors),
        size = Size(w, h),
    )

    // 2. 計算視窗中心與 FOV
    val sunsetAz = trail.last().azimuthDegrees
    val centerAz = midpointAzimuth(towerBearing, sunsetAz)
    val fovH = 60.0
    val horizonY = h * 0.78f

    // 仰角範圍：太陽軌跡 60min 內最高約 12°（冬季夕陽）；保留 12° 給 headroom
    val maxAlt = trail.maxOf { it.altitudeDegrees }
    val vFov = (maxAlt + 2.0).coerceAtLeast(10.0)

    // 3. 地平線
    drawLine(
        Color.Black.copy(alpha = 0.45f),
        Offset(0f, horizonY),
        Offset(w, horizonY),
        strokeWidth = 1.2f,
    )

    // 4. 主塔垂直線（在塔方位 X 處畫直線）
    val towerX = bearingToX(towerBearing, centerAz, fovH, w)
    if (towerX != null) {
        // 塔色依對齊狀態變色，一眼看出是否對得到
        val towerColor = when (classification) {
            AlignmentClass.PERFECT -> Color(0xFF4ADE80)  // 綠（完美對齊）
            AlignmentClass.NEAR    -> Color(0xFFFFD66B)  // 黃（接近）
            else                   -> Color(0xFFE63946)  // 紅（偏離）
        }
        val towerAngularHeightDeg = Math.toDegrees(
            atan(BridgeTower.TOWER_TIP_ELEVATION_M / distanceToTowerMeters.coerceAtLeast(1.0)),
        )
        val towerTopY = (horizonY - (towerAngularHeightDeg / vFov * horizonY).toFloat())
            .coerceIn(0f, horizonY - 3f)
        drawLine(
            towerColor.copy(alpha = 0.95f),
            Offset(towerX, towerTopY),
            Offset(towerX, horizonY),
            strokeWidth = 2.2f,
        )
        // 塔頂小點
        drawCircle(towerColor, 2.5f, Offset(towerX, towerTopY))
    }

    // 5. 天體軌跡 13 點（太陽暖黃 / 月亮銀藍）
    val glowColor = if (isMoon) Color(0xFFCAD6FF) else Color(0xFFFFD66B)
    val coreColor = if (isMoon) Color(0xFFEDF0FA) else Color(0xFFFFE19A)
    trail.forEachIndexed { i, p ->
        val x = bearingToX(p.azimuthDegrees, centerAz, fovH, w) ?: return@forEachIndexed
        val y = horizonY - (p.altitudeDegrees / vFov * horizonY).toFloat()
        val isLast = i == trail.lastIndex
        val ratio = i.toFloat() / (trail.size - 1).coerceAtLeast(1)
        // 越接近穿塔瞬間越亮
        val alpha = lerpF(0.35f, 1.0f, ratio)
        val radius = if (isLast) 4f else lerpF(1.5f, 2.5f, ratio)
        if (isLast) {
            // 光暈
            drawCircle(glowColor.copy(alpha = 0.45f), 7f, Offset(x, y))
            drawCircle(glowColor.copy(alpha = 0.65f), 5f, Offset(x, y))
        }
        drawCircle(coreColor.copy(alpha = alpha), radius, Offset(x, y))
    }
}

/** 方位角轉畫面 X 座標。若超出 FOV 範圍回傳 null。 */
private fun DrawScope.bearingToX(
    bearing: Double, centerAz: Double, fovH: Double, w: Float,
): Float? {
    val delta = ((bearing - centerAz + 540.0) % 360.0) - 180.0
    if (delta < -fovH / 2 || delta > fovH / 2) return null
    return ((delta + fovH / 2) / fovH * w).toFloat()
}

/** 兩個方位角的中點，正確處理 0/360 邊界。 */
private fun midpointAzimuth(a: Double, b: Double): Double {
    val diff = ((b - a + 540.0) % 360.0) - 180.0
    return (a + diff / 2.0 + 360.0) % 360.0
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

/**
 * 開啟 Google Maps 導航到指定座標。
 * 優先啟動 Google Maps App；若未安裝則 fallback 到瀏覽器。
 */
private fun openGoogleMaps(ctx: Context, lat: Double, lon: Double, label: String) {
    // geo URI：Google Maps 可解讀 label 參數顯示圖釘名稱
    val encodedLabel = Uri.encode(label)
    val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($encodedLabel)")
    val mapsIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    if (mapsIntent.resolveActivity(ctx.packageManager) != null) {
        ctx.startActivity(mapsIntent)
    } else {
        // fallback：以瀏覽器打開（也可導航）
        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon")
        ctx.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}
