package studio.freestyle.labs.danjiangsunseeker.presentation.hotspot

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.hilt.navigation.compose.hiltViewModel
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.AlignmentClass
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.SunsetScore
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
                Toast.makeText(ctx, "匯出完成", Toast.LENGTH_SHORT).show()
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

    state.importMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            vm.clearImportMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    headerLabel(state.date, today),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = "匯入")
                }
                IconButton(onClick = {
                    val fname = "hotspots_${today.format(DateTimeFormatter.ofPattern("yyyyMMdd"))}.json"
                    exportLauncher.launch(fname)
                }) {
                    Icon(Icons.Outlined.Upload, contentDescription = "匯出")
                }
                IconButton(onClick = { vm.showEditor() }) {
                    Icon(Icons.Outlined.Add, contentDescription = "新增熱點")
                }
            }
            Spacer(Modifier.height(8.dp))
            DateChipRow(
                selectedDate = state.date,
                today = today,
                onPickQuick = { vm.loadFor(it) },
                onOpenPicker = { showDatePicker = true },
            )
            Spacer(Modifier.height(8.dp))

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.predictions, key = { it.prediction.hotspot.id }) { p ->
                        HotspotRow(
                            p,
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
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) { DatePicker(state = pickerState) }
    }

    state.editor?.let { ed ->
        if (ed.showLocationPicker) {
            // ── 地圖選點覆蓋層（全螢幕）────────────────────────────────
            LocationPickerOverlay(
                initLat = ed.latitude.toDoubleOrNull() ?: BridgeTower.LATITUDE,
                initLon = ed.longitude.toDoubleOrNull() ?: BridgeTower.LONGITUDE,
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

private fun headerLabel(selectedDate: LocalDate, today: LocalDate): String {
    val diff = selectedDate.toEpochDay() - today.toEpochDay()
    val prefix = when (diff) {
        0L -> "今日"
        1L -> "明日"
        2L -> "後日"
        in 3L..30L -> "$diff 天後"
        in -7L..-1L -> "${-diff} 天前"
        else -> ""
    }
    val dateText = selectedDate.format(DateTimeFormatter.ofPattern("M/d (E)", Locale.TAIWAN))
    return if (prefix.isNotEmpty()) "$prefix · $dateText" else dateText
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DateChipRow(
    selectedDate: LocalDate,
    today: LocalDate,
    onPickQuick: (LocalDate) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val quickDates = listOf(
        "今天" to today,
        "明天" to today.plusDays(1),
        "後天" to today.plusDays(2),
        "週末" to nextSaturday(today),
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
        AssistChip(
            onClick = onOpenPicker,
            label = { Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) },
            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            colors = AssistChipDefaults.assistChipColors(),
        )
    }
}

private fun nextSaturday(from: LocalDate): LocalDate {
    var d = from
    while (d.dayOfWeek != java.time.DayOfWeek.SATURDAY) d = d.plusDays(1)
    return d
}

@Composable
private fun HotspotRow(
    p: ScoredPrediction,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onNavigate: () -> Unit,
) {
    val name = p.prediction.hotspot.nameRes?.let { stringResource(it) }
        ?: p.prediction.hotspot.customName.orEmpty()
    val sunsetTime = p.prediction.events.sunset?.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    val offset = p.prediction.alignmentOffsetDegrees
    val isOverride = p.isOverride
    val isPureCustom = p.prediction.hotspot.isCustom && p.prediction.hotspot.id.startsWith("custom_")
    val isTooFar = p.prediction.classification == AlignmentClass.TOO_FAR

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isTooFar) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            // Top 對齊：當 Column 內容因大字體而變高時，Badge 與按鈕靠頂，不會被強制拉伸
            verticalAlignment = Alignment.Top,
        ) {
            if (isTooFar) TooFarBadge() else ScoreBadge(p.score, scoreBadgeColor(p.score.overall))
            Spacer(Modifier.width(8.dp))

            // ── 日落前一小時太陽軌跡縮圖（TOO_FAR 或無軌跡時顯示空白佔位）
            HotspotThumbnail(
                trail = p.prediction.lastHourSunTrail,
                towerBearing = p.prediction.bearingToTowerDegrees,
                distanceToTowerMeters = p.prediction.distanceToTowerMeters,
                classification = p.prediction.classification,
                modifier = Modifier
                    .width(72.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(8.dp))

            // ── 主要內容：全部垂直堆疊，任何字體大小都不會橫向溢出 ──────
            Column(modifier = Modifier.weight(1f)) {
                // 名稱 — 自動換行，不放在 Row 內避免溢出
                Text(
                    text = name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                // 自訂 / 已修改 標籤放在名稱下方獨立一行
                if (isPureCustom) {
                    Text(
                        "(自訂)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else if (isOverride) {
                    Text(
                        "(已修改)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                Spacer(Modifier.height(4.dp))

                if (isTooFar) {
                    Text(
                        "太遠 — 距主塔 ${(p.prediction.distanceToTowerMeters / 1000.0).format(1)} km，超過 25 km 限制",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    // 日落時間與偏差各佔一行，避免放在同一 Row 時大字體溢出
                    Text(
                        "${stringResource(R.string.label_sunset_time)} $sunsetTime",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    offset?.let {
                        Text(
                            "對齊偏差 ${"%+.2f".format(it)}°",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "距主塔 ${(p.prediction.distanceToTowerMeters / 1000.0).format(2)} km · ${p.score.verdict}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // ── 操作按鈕：垂直排列，大字體時與高 Column 並列不擠版 ────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "編輯")
                }
                IconButton(onClick = onNavigate) {
                    Icon(
                        Icons.Outlined.Directions,
                        contentDescription = "Google Maps 導航",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: SunsetScore, color: Color) {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).background(color),
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
        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline),
        contentAlignment = Alignment.Center,
    ) {
        Text("遠", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                if (state.isEditing) "編輯熱點" else "新增熱點",
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onChange(EditorField.NAME, it) },
                    label = { Text("名稱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.latitude,
                        onValueChange = { onChange(EditorField.LAT, it) },
                        label = { Text("緯度") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.longitude,
                        onValueChange = { onChange(EditorField.LON, it) },
                        label = { Text("經度") },
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
                    Text("在地圖上選取座標")
                }
                OutlinedTextField(
                    value = state.elevation,
                    onValueChange = { onChange(EditorField.ELEV, it) },
                    label = { Text("海拔高度 (m)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { onChange(EditorField.DESC, it) },
                    label = { Text("描述 (選填)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.originalIsDefault) {
                    Text(
                        "編輯預設熱點：你的修改會保存為覆寫；按「重置」可回復預設值。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else {
                    Text(
                        "提示：可在「地圖」分頁點選位置看座標，再回來填入",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("儲存") } },
        dismissButton = {
            Row {
                if (state.canDelete) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(4.dp))
                        Text(
                            if (state.canResetToDefault) "重置" else "刪除",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
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
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickedLat by remember { mutableStateOf<Double?>(null) }
    var pickedLon by remember { mutableStateOf<Double?>(null) }
    var mapRef    by remember { mutableStateOf<MapLibreMap?>(null) }

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
                    },
                    onMapClick = { lat, lon ->
                        pickedLat = lat
                        pickedLon = lon
                        // 移動相機到點選位置
                        mapRef?.moveCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newLatLng(LatLng(lat, lon))
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
                            Icon(Icons.Outlined.Close, contentDescription = "取消")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("點選地圖選取位置", style = MaterialTheme.typography.titleSmall)
                            if (pickedLat != null) {
                                Text(
                                    "📍 ${"%.6f".format(pickedLat)}, ${"%.6f".format(pickedLon)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    "尚未選取（點擊地圖）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val lat = pickedLat ?: return@Button
                                val lon = pickedLon ?: return@Button
                                onConfirm(lat, lon)
                            },
                            enabled = pickedLat != null,
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("確認")
                        }
                    }
                }

                // ── 中心準星（視覺輔助，非選點依據）──────────────────
                if (pickedLat != null) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.Center)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
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
        drawSunTrailThumbnail(trail, towerBearing, distanceToTowerMeters, classification)
    }
}

private fun DrawScope.drawSunTrailThumbnail(
    trail: List<SunTrailPoint>,
    towerBearing: Double,
    distanceToTowerMeters: Double,
    classification: AlignmentClass,
) {
    val w = size.width
    val h = size.height

    // 1. 天空漸層（日落色）
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                Color(0xFFC0392B),       // 紅
                Color(0xFFFF8C42),       // 橙
                Color(0xFF1A0A3A),       // 深藍紫
            ),
        ),
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

    // 5. 太陽軌跡 13 點
    trail.forEachIndexed { i, p ->
        val x = bearingToX(p.azimuthDegrees, centerAz, fovH, w) ?: return@forEachIndexed
        val y = horizonY - (p.altitudeDegrees / vFov * horizonY).toFloat()
        val isLast = i == trail.lastIndex
        val ratio = i.toFloat() / (trail.size - 1).coerceAtLeast(1)
        // 越接近日落越亮
        val alpha = lerpF(0.35f, 1.0f, ratio)
        val radius = if (isLast) 4f else lerpF(1.5f, 2.5f, ratio)
        if (isLast) {
            // 光暈
            drawCircle(Color(0xFFFFD66B).copy(alpha = 0.45f), 7f, Offset(x, y))
            drawCircle(Color(0xFFFFD66B).copy(alpha = 0.65f), 5f, Offset(x, y))
        }
        drawCircle(Color(0xFFFFE19A).copy(alpha = alpha), radius, Offset(x, y))
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
