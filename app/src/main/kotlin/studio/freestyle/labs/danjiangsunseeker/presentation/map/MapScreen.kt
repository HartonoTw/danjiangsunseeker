package studio.freestyle.labs.danjiangsunseeker.presentation.map

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MyLocation
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
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
    LaunchedEffect(mapHolder.styleVersion, state.goldenLine) {
        if (mapHolder.styleVersion == 0) return@LaunchedEffect
        val style = mapHolder.style ?: return@LaunchedEffect
        MapLayers.updateGoldenLine(style, state.goldenLine)
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
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    state.selectedDate.format(dateFmt),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(8.dp))
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
            state.tap?.let { tap -> TapAnalysisCard(tap, onClose = { vm.clearTap() }) }
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
}

@Composable
private fun SunSummaryCard(state: MapUiState) {
    val az = state.sunsetAzimuthAtTower
    val bearing = state.goldenLine?.bearingFromTowerDegrees
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
                "黃金射線: ${bearing?.let { "%.2f°".format(it) } ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
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
private fun TapAnalysisCard(tap: TapAnalysis, onClose: () -> Unit) {
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
                "日落方位偏差: ${tap.alignmentOffsetDegrees?.let { "%+.2f°".format(it) } ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
            )
            tap.targetTime?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${tap.towerTarget.displayName}: %02d:%02d".format(it.hour, it.minute),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(verdict, color = verdictColor, style = MaterialTheme.typography.bodySmall)
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
