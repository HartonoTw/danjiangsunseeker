package studio.freestyle.labs.danjiangsunseeker.presentation.map

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NavigateBefore
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun MapScreen(vm: MapViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    val mapHolder = remember { MapHolder() }
    var showDatePicker by remember { mutableStateOf(false) }
    // 連續播放方向：-1 往前、0 停止、+1 往後。
    // playLevel：0 停止、1=0.5s、2=0.1s、3=0.05s，每點一次往上跳，超過 3 則停止。
    var playDirection by remember { mutableIntStateOf(0) }
    var playLevel by remember { mutableIntStateOf(0) }

    LaunchedEffect(playDirection, playLevel) {
        if (playDirection == 0 || playLevel == 0) return@LaunchedEffect
        val delayMs = when (playLevel) {
            1 -> 500L
            2 -> 100L
            else -> 50L
        }
        while (true) {
            delay(delayMs)
            vm.stepDate(playDirection.toLong())
        }
    }

    // 點擊連續播放鈕：同方向則加速 (level+1)，超過 3 停止；不同方向則從 level 1 起跳。
    fun cyclePlay(direction: Int) {
        if (playDirection == direction) {
            if (playLevel >= 3) {
                playDirection = 0
                playLevel = 0
            } else {
                playLevel += 1
            }
        } else {
            playDirection = direction
            playLevel = 1
        }
    }
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.flyToCurrentLocation()
        else Toast.makeText(ctx, "未授權位置權限", Toast.LENGTH_SHORT).show()
    }

    // 雙 key LaunchedEffect：
    //   styleVersion 變（地圖就緒）→ 重新同步當下的 mergedHotspots / goldenLine
    //   mergedHotspots 變（編輯熱點）→ 立刻更新地圖，而不等 styleVersion 改變
    // 兩個 key 任一改變都會重新執行 body，且 body 執行時讀的是執行當下的最新 state。
    LaunchedEffect(mapHolder.styleVersion, state.mergedHotspots) {
        Log.d("MapDebug", "LE[hotspots] fire: styleVersion=${mapHolder.styleVersion} mergedHotspots.size=${state.mergedHotspots.size}")
        if (mapHolder.styleVersion == 0) {
            Log.d("MapDebug", "LE[hotspots] early-return: map not ready")
            return@LaunchedEffect
        }
        val style = mapHolder.style ?: run {
            Log.d("MapDebug", "LE[hotspots] early-return: style null")
            return@LaunchedEffect
        }
        Log.d("MapDebug", "LE[hotspots] -> updateHotspots, ids=${state.mergedHotspots.map { it.id }}")
        MapLayers.updateHotspots(style, state.mergedHotspots, ctx)
    }
    LaunchedEffect(mapHolder.styleVersion, state.goldenLine, state.towerTopGoldenLine) {
        if (mapHolder.styleVersion == 0) return@LaunchedEffect
        val style = mapHolder.style ?: return@LaunchedEffect
        MapLayers.updateGoldenLine(style, state.goldenLine)
        MapLayers.updateTowerTopGoldenLine(style, state.towerTopGoldenLine)
    }
    LaunchedEffect(mapHolder.styleVersion, state.tap) {
        if (mapHolder.styleVersion == 0) return@LaunchedEffect
        val style = mapHolder.style ?: return@LaunchedEffect
        MapLayers.updateTapMark(style, state.tap?.point?.latitude, state.tap?.point?.longitude)
    }
    LaunchedEffect(mapHolder.styleVersion, state.currentLocationFlyRequest) {
        if (mapHolder.styleVersion == 0 || state.currentLocationFlyRequest == 0) return@LaunchedEffect
        val map = mapHolder.map ?: return@LaunchedEffect
        val point = state.currentLocation ?: return@LaunchedEffect
        val zoom = map.cameraPosition.zoom.coerceAtLeast(15.5)
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), zoom),
            900,
        )
    }
    state.locationMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            vm.clearLocationMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ComposeMapLibre(
            modifier = Modifier.fillMaxSize(),
            onMapReady = { map, style ->
                Log.d("MapDebug", "onMapReady: state.mergedHotspots.size=${state.mergedHotspots.size} ids=${state.mergedHotspots.map { it.id }}")
                mapHolder.map = map
                mapHolder.style = style
                // install() 只做「add-if-not-exists」，在此同步呼叫一次，保證首幀就有標記
                MapLayers.install(ctx, style, state.mergedHotspots)
                MapLayers.updateGoldenLine(style, state.goldenLine)
                MapLayers.updateTowerTopGoldenLine(style, state.towerTopGoldenLine)
                mapHolder.styleVersion += 1
                Log.d("MapDebug", "onMapReady: styleVersion bumped to ${mapHolder.styleVersion}")
                // 初始相機定位在主塔上方
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(25.17, 121.42))
                    .zoom(12.5)
                    .build()
            },
            onMapClick = { lat, lon -> vm.onMapTap(lat, lon) },
        )

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                // << 連續往前播放（每點一次加速：0.5s→0.1s→0.05s→停止）
                DatePlayButton(
                    forward = false,
                    activeLevel = if (playDirection == -1) playLevel else 0,
                    onClick = { cyclePlay(-1) },
                )
                // < 往前一天
                DateStepButton(
                    icon = Icons.Outlined.NavigateBefore,
                    contentDescription = "前一天",
                    active = false,
                    onClick = {
                        playDirection = 0
                        playLevel = 0
                        vm.stepDate(-1)
                    },
                )
                Text(
                    state.selectedDate.format(dateFmt),
                    style = MaterialTheme.typography.titleMedium,
                )
                // > 往後一天
                DateStepButton(
                    icon = Icons.Outlined.NavigateNext,
                    contentDescription = "後一天",
                    active = false,
                    onClick = {
                        playDirection = 0
                        playLevel = 0
                        vm.stepDate(1)
                    },
                )
                // >> 連續往後播放（每點一次加速：0.5s→0.1s→0.05s→停止）
                DatePlayButton(
                    forward = true,
                    activeLevel = if (playDirection == 1) playLevel else 0,
                    onClick = { cyclePlay(1) },
                )
                Spacer(Modifier.width(4.dp))
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text("選日期") },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 82.dp, end = 16.dp)
                .size(48.dp)
                .shadow(elevation = 4.dp, shape = CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            IconButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) vm.flyToCurrentLocation()
                    else locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                enabled = !state.locatingCurrentLocation,
            ) {
                if (state.locatingCurrentLocation) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.MyLocation, contentDescription = "飛到目前位置")
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SunSummaryCard(state)
            state.tap?.let { tap ->
                TapAnalysisCard(
                    tap = tap,
                    onAddHotspot = { vm.showAddTapHotspotDialog() },
                    onClose = { vm.clearTap() },
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDate
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
                        vm.setDate(date)
                    }
                    showDatePicker = false
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    state.hotspotDraft?.let { draft ->
        AddHotspotDialog(
            draft = draft,
            onChange = vm::updateHotspotDraft,
            onSave = vm::saveHotspotDraft,
            onDismiss = vm::closeHotspotDraft,
        )
    }
}

@Composable
private fun SunSummaryCard(state: MapUiState) {
    val az = state.sunsetAzimuthAtTower
    val baseBearing = state.goldenLine?.bearingFromTowerDegrees
    val topBearing = state.towerTopGoldenLine?.bearingFromTowerDegrees
    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                "日落方位 (從主塔看): ${az?.let { "%.2f°".format(it) } ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "黃金線 塔基: ${baseBearing?.let { "%.2f°".format(it) } ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "黃金線 塔頂: ${topBearing?.let { "%.2f°".format(it) } ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "點地圖上任一點，APP 會告訴你「站在這拍夕陽穿塔會差幾度」",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun TapAnalysisCard(
    tap: TapAnalysis,
    onAddHotspot: () -> Unit,
    onClose: () -> Unit,
) {
    val offset = tap.alignmentOffsetDegrees
    val (verdict: String, verdictColor: Color) = when {
        offset == null -> "—" to MaterialTheme.colorScheme.outline
        abs(offset) < 0.5 -> "幾乎完美對齊主塔，這就是黃金拍攝點！" to MaterialTheme.colorScheme.primary
        abs(offset) < 2.0 -> "接近黃金拍攝帶 (±${"%.2f".format(offset)}°)" to MaterialTheme.colorScheme.secondary
        else -> "日落方位偏移 ${"%.1f".format(offset)}°，建議沿橘色射線移動" to MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("選定座標", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onAddHotspot, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = "新增熱點", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "關閉", modifier = Modifier.size(18.dp))
                }
            }
            Text(
                "${"%.5f".format(tap.point.latitude)}°N, ${"%.5f".format(tap.point.longitude)}°E",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("距主塔: ${"%.2f km".format(tap.distanceToTowerMeters / 1000.0)}", style = MaterialTheme.typography.bodySmall)
                Text("主塔方位: ${"%.2f°".format(tap.bearingToTowerDegrees)}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "塔基偏差: ${tap.lowerAlignmentOffsetDegrees?.let { "%+.2f°".format(it) } ?: "—"}" +
                    " · ${tap.lowerTargetTime?.let { "時間 %02d:%02d".format(it.hour, it.minute) } ?: "時間 —"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "塔頂偏差: ${tap.upperAlignmentOffsetDegrees?.let { "%+.2f°".format(it) } ?: "—"}" +
                    " · ${tap.upperTargetTime?.let { "時間 %02d:%02d".format(it.hour, it.minute) } ?: "時間 —"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(verdict, color = verdictColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AddHotspotDialog(
    draft: MapHotspotDraft,
    onChange: (MapHotspotDraftField, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增熱點") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onChange(MapHotspotDraftField.Name, it) },
                    label = { Text("名稱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${draft.latitude}°N, ${draft.longitude}°E",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                OutlinedTextField(
                    value = draft.elevation,
                    onValueChange = { onChange(MapHotspotDraftField.Elevation, it) },
                    label = { Text("海拔高度 (m)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { onChange(MapHotspotDraftField.Description, it) },
                    label = { Text("描述 (選填)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                draft.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("儲存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 日期播放控制鈕：< > 單日跳、<< >> 連續跳（active 時高亮）。 */
@Composable
private fun DateStepButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 連續播放鈕：以箭頭數量表示速度等級 (|> = 0.5s、|>|> = 0.1s、|>|>|> = 0.05s)。
 * 未啟用時顯示單一暗色箭頭。forward=false 時水平鏡射成往前 (<|)。
 */
@Composable
private fun DatePlayButton(
    forward: Boolean,
    activeLevel: Int,
    onClick: () -> Unit,
) {
    val active = activeLevel > 0
    val count = if (active) activeLevel else 1
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-4).dp),
    ) {
        repeat(count) {
            Icon(
                Icons.Outlined.PlayArrow,
                contentDescription = if (forward) "連續往後" else "連續往前",
                modifier = Modifier
                    .size(18.dp)
                    .then(if (!forward) Modifier.scale(-1f, 1f) else Modifier),
                tint = tint,
            )
        }
    }
}

/** 持有 Compose 重組之間共用的 MapLibre 物件參考。 */
private class MapHolder {
    var map: MapLibreMap? = null
    var style: Style? = null
    /** styleVersion 每次 Style 重新載入時遞增，作為 LaunchedEffect 的 key。 */
    var styleVersion: Int by mutableIntStateOf(0)
}

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
