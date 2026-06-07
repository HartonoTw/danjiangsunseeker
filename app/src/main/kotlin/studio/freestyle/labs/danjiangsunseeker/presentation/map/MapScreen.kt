package studio.freestyle.labs.danjiangsunseeker.presentation.map

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.model.TideKind
import studio.freestyle.labs.danjiangsunseeker.presentation.common.MoonPhaseIcon
import studio.freestyle.labs.danjiangsunseeker.presentation.common.lunarPhaseLabel
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
        else Toast.makeText(ctx, ctx.getString(R.string.toast_location_permission_denied), Toast.LENGTH_SHORT).show()
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
    LaunchedEffect(mapHolder.styleVersion, state.goldenLine, state.towerTopGoldenLine, state.moonGoldenLine) {
        if (mapHolder.styleVersion == 0) return@LaunchedEffect
        val style = mapHolder.style ?: return@LaunchedEffect
        MapLayers.updateGoldenLine(style, state.goldenLine)
        MapLayers.updateTowerTopGoldenLine(style, state.towerTopGoldenLine)
        MapLayers.updateMoonGoldenLine(style, state.moonGoldenLine)
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
                MapLayers.updateMoonGoldenLine(style, state.moonGoldenLine)
                mapHolder.styleVersion += 1
                Log.d("MapDebug", "onMapReady: styleVersion bumped to ${mapHolder.styleVersion}")
                // 初始相機定位在主塔上方
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(25.17, 121.42))
                    .zoom(12.5)
                    .build()
            },
            onMapClick = { lat, lon ->
                // 點擊＝把地圖飛到該點，中央準心（X）隨即對準選定位置（與「熱點」選點覆蓋層一致）。
                mapHolder.map?.animateCamera(
                    CameraUpdateFactory.newLatLng(LatLng(lat, lon)),
                    300,
                )
                vm.onMapTap(lat, lon)
            },
        )

        // 畫面正中央的固定準心（X）：點地圖移動把目標對到此處，新增熱點時以「地圖中心」座標存檔。
        MapCenterReticle()

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
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
                    contentDescription = stringResource(R.string.cd_step_prev_day),
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
                    contentDescription = stringResource(R.string.cd_step_next_day),
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
                    label = { Text(stringResource(R.string.map_pick_date)) },
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
                    Icon(Icons.Outlined.MyLocation, contentDescription = stringResource(R.string.cd_fly_to_current_location))
                }
            }
        }

        // 新增熱點：以準心所在的「地圖中心」座標開啟對話框
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 142.dp, end = 16.dp)
                .size(48.dp)
                .shadow(elevation = 4.dp, shape = CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            IconButton(
                onClick = {
                    val target = mapHolder.map?.cameraPosition?.target ?: return@IconButton
                    vm.showAddHotspotDialog(target.latitude, target.longitude)
                },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.hotspot_add_title))
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
                    premium = state.premiumUnlocked,
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
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
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

/** 畫面正中央的固定準心（白描邊紅色 X + 中心圓點），標示新增熱點時採用的「地圖中心」位置。 */
@Composable
private fun BoxScope.MapCenterReticle() {
    Canvas(
        modifier = Modifier
            .align(Alignment.Center)
            .size(40.dp),
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        val arm = size.minDimension / 2f - 4.dp.toPx()
        val gap = arm * 0.30f
        val white = Color.White.copy(alpha = 0.95f)
        val red = Color(0xFFD72638)

        // X 形準線（中心留空）：四條對角線段（45° 投影 → ×0.7071）
        val diag = arm * 0.7071f
        val gd = gap * 0.7071f
        val segments = listOf(
            Offset(cx - diag, cy - diag) to Offset(cx - gd, cy - gd),
            Offset(cx + gd, cy + gd) to Offset(cx + diag, cy + diag),
            Offset(cx - diag, cy + diag) to Offset(cx - gd, cy + gd),
            Offset(cx + gd, cy - gd) to Offset(cx + diag, cy - diag),
        )
        segments.forEach { (a, b) ->
            drawLine(white, a, b, strokeWidth = 3.5.dp.toPx())
            drawLine(red, a, b, strokeWidth = 1.5.dp.toPx())
        }
        // 中心圓點
        drawCircle(white, radius = 3.dp.toPx(), center = center)
        drawCircle(red, radius = 2.dp.toPx(), center = center)
    }
}

@Composable
private fun SunSummaryCard(state: MapUiState) {
    val az = state.sunsetAzimuthAtTower
    val baseBearing = state.goldenLine?.bearingFromTowerDegrees
    val topBearing = state.towerTopGoldenLine?.bearingFromTowerDegrees
    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            val none = stringResource(R.string.value_none)
            Text(
                stringResource(R.string.map_sunset_azimuth_from_tower, az?.let { "%.2f°".format(it) } ?: none),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(
                    R.string.map_golden_line_both,
                    baseBearing?.let { "%.2f°".format(it) } ?: none,
                    topBearing?.let { "%.2f°".format(it) } ?: none,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            // ── 月象・潮汐・月亮黃金帶 (付費功能；鎖定時不顯示) ──────────────
            if (state.premiumUnlocked) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(
                    stringResource(
                        R.string.map_moon_line,
                        state.moonGoldenLine?.bearingFromTowerDegrees?.let { "%.2f°".format(it) } ?: none,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                state.moonInfo?.let { moon ->
                    Spacer(Modifier.height(2.dp))
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val tide = state.tideInfo
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.map_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 把時間格式化為「時間 HH:MM」字串；無時間時以 [none] 佔位。 */
@Composable
private fun timeValueOrNone(time: java.time.ZonedDateTime?, none: String): String =
    stringResource(
        R.string.map_time_value,
        time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: none,
    )

@Composable
private fun TapAnalysisCard(
    tap: TapAnalysis,
    premium: Boolean,
    onAddHotspot: () -> Unit,
    onClose: () -> Unit,
) {
    val offset = tap.alignmentOffsetDegrees
    val (verdict: String, verdictColor: Color) = when {
        offset == null -> stringResource(R.string.value_none) to MaterialTheme.colorScheme.outline
        abs(offset) < 0.5 -> stringResource(R.string.map_verdict_perfect) to MaterialTheme.colorScheme.primary
        abs(offset) < 2.0 -> stringResource(R.string.map_verdict_near, "%.2f".format(abs(offset))) to MaterialTheme.colorScheme.secondary
        else -> stringResource(R.string.map_verdict_far, "%.1f".format(offset)) to MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.map_selected_coords), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onAddHotspot, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.hotspot_add_title), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_close), modifier = Modifier.size(18.dp))
                }
            }
            val none = stringResource(R.string.value_none)
            Text(
                stringResource(R.string.coords_lat_lon, "%.5f".format(tap.point.latitude), "%.5f".format(tap.point.longitude)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.map_distance_to_tower, "%.2f".format(tap.distanceToTowerMeters / 1000.0)), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.map_tower_bearing, "%.2f".format(tap.bearingToTowerDegrees)), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(2.dp))
            // 塔基 / 塔頂 太陽對齊偏差與時間（同一行）
            Text(
                stringResource(
                    R.string.map_sun_offsets,
                    tap.lowerAlignmentOffsetDegrees?.let { "%+.2f°".format(it) } ?: none,
                    timeValueOrNone(tap.lowerTargetTime, none),
                    tap.upperAlignmentOffsetDegrees?.let { "%+.2f°".format(it) } ?: none,
                    timeValueOrNone(tap.upperTargetTime, none),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            // 月亮穿塔 (付費功能)：塔基 / 塔頂 月亮對齊偏差與時間（同一行）
            if (premium) {
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(
                        R.string.map_moon_offsets,
                        tap.moonLowerAlignmentOffsetDegrees?.let { "%+.2f°".format(it) } ?: none,
                        timeValueOrNone(tap.moonLowerTime, none),
                        tap.moonUpperAlignmentOffsetDegrees?.let { "%+.2f°".format(it) } ?: none,
                        timeValueOrNone(tap.moonUpperTime, none),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
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
        title = { Text(stringResource(R.string.hotspot_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onChange(MapHotspotDraftField.Name, it) },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.coords_lat_lon, draft.latitude, draft.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                OutlinedTextField(
                    value = draft.elevation,
                    onValueChange = { onChange(MapHotspotDraftField.Elevation, it) },
                    label = { Text(stringResource(R.string.field_elevation)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { onChange(MapHotspotDraftField.Description, it) },
                    label = { Text(stringResource(R.string.field_description_optional)) },
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
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
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
                contentDescription = stringResource(
                    if (forward) R.string.cd_step_next_continuous else R.string.cd_step_prev_continuous,
                ),
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
