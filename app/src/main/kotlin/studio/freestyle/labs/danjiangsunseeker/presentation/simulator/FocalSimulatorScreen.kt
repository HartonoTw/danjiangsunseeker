package studio.freestyle.labs.danjiangsunseeker.presentation.simulator

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.SensorSpec
import studio.freestyle.labs.danjiangsunseeker.presentation.common.TowerTargetSelector
import java.time.Instant
import java.time.ZoneId

@Composable
fun FocalSimulatorScreen(vm: FocalSimulatorViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }

    // 位置權限：拿到後立刻重試 loadGps()，讓「目前位置」選項可用
    val locationPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.loadGps()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.focal_title), style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HotspotPicker(
                current = state.hotspot,
                hotspots = state.mergedHotspots,
                gpsReady = state.gpsPoint != null,
                onPick = vm::setHotspot,
                onRequestGps = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        ctx, android.Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) vm.loadGps()
                    else locationPermLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                },
            )
            AssistChip(
                onClick = { showDatePicker = true },
                label = { Text(state.date.toString()) },
            )
        }

        TowerTargetSelector(
            selected = state.towerTarget,
            onSelect = vm::setTowerTarget,
        )

        SensorPicker(current = state.sensor, onPick = vm::setSensor)

        Text(stringResource(R.string.focal_focal_length, state.focalLengthMm.toInt()), style = MaterialTheme.typography.titleMedium)
        Slider(
            value = state.focalLengthMm.toFloat().coerceAtMost(300f),
            onValueChange = { vm.setFocalLength(it.toDouble()) },
            valueRange = 14f..300f,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.focal_time, state.selectedTimeLabel), style = MaterialTheme.typography.titleMedium)
            state.sunsetTime?.let {
                Text(
                    stringResource(R.string.focal_sunset_label, "%02d:%02d".format(it.hour, it.minute)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        // 範圍 12:00–19:30；採非線性映射，日落前 1.5 小時起的「細調區」佔較大滑桿比例，
        // 讓接近日落時拖動的時間粒度更細。日落點以紅色刻度標在滑桿上。
        val sunsetMin = state.sunsetTime?.let { it.hour * 60 + it.minute }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val thumbRadius = 10.dp
            Slider(
                value = minuteToSliderFraction(state.timeMinuteOfDay, sunsetMin),
                onValueChange = { vm.setMinuteOfDay(sliderFractionToMinute(it, sunsetMin)) },
                valueRange = 0f..1f,
            )
            // 日落刻度：依非線性映射換算日落在軌道上的位置（扣掉左右各 thumbRadius 內縮）
            sunsetMin?.let { sm ->
                val frac = minuteToSliderFraction(sm, sunsetMin)
                val markerCenterX = thumbRadius + (maxWidth - thumbRadius * 2f) * frac
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = markerCenterX - 1.dp)
                        .width(2.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.error),
                )
                Text(
                    "日落",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (markerCenterX - 12.dp).coerceAtLeast(0.dp)),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("12:00", style = MaterialTheme.typography.labelSmall)
            val endMinute = timeSliderBounds(sunsetMin).maxMinute
            Text(
                "%02d:%02d".format(endMinute / 60, endMinute % 60),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        FrameCanvas(state)

        Text(
            stringResource(
                R.string.focal_geometry,
                "%.2f".format(state.distanceKm),
                "%.1f".format(state.horizontalFovDegrees),
                "%.1f".format(state.verticalFovDegrees),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(
                R.string.focal_sun_info,
                "%.2f".format(state.sunAzimuthDegrees),
                "%+.2f".format(state.sunAltitudeDegrees),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
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
                        vm.setDate(date)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun FrameCanvas(state: FocalSimulatorState) {
    val baliLabel = stringResource(R.string.bridge_label_bali)
    val tamsuiLabel = stringResource(R.string.bridge_label_tamsui)
    val towerHeightLabel = stringResource(R.string.focal_tower_height_label)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        val w = size.width
        val h = size.height
        drawSky(state.sunAltitudeDegrees, w, h)
        drawBridge(state, w, h)
        drawSunTrail(state, w, h)
        drawSun(state, w, h)
        drawOffFrameArrows(state.sun, w, h)
        drawBridgeLabels(state, w, h, baliLabel, tamsuiLabel, towerHeightLabel)
    }
}

private fun DrawScope.drawSky(sunAltitudeDeg: Double, w: Float, h: Float) {
    val (top, bottom) = when {
        sunAltitudeDeg > 10 -> Color(0xFF3A7FC1) to Color(0xFF8BBDE8)   // 白天藍天
        sunAltitudeDeg > 2  -> Color(0xFFD4622A) to Color(0xFFFFB347)   // 黃昏
        sunAltitudeDeg > 0  -> Color(0xFFC0392B) to Color(0xFFFF8C42)   // 日落瞬間
        sunAltitudeDeg > -6 -> Color(0xFF1A0A3A) to Color(0xFF8B3A62)   // 藍調時刻
        else                -> Color(0xFF060412) to Color(0xFF0F0824)   // 夜空
    }
    drawRect(brush = Brush.verticalGradient(listOf(top, bottom)), size = Size(w, h))
}

/** 繪製淡江大橋：引橋 + 斜張索 + 橋面 + A 字主塔 + 水面倒影 */
private fun DrawScope.drawBridge(state: FocalSimulatorState, w: Float, h: Float) {
    // 相機對準主塔，塔永遠在畫面水平中央
    val midX = w / 2f
    val topY   = (state.towerTopYFrac.toFloat()    * h).coerceIn(0f, h)
    val deckY  = (state.deckYFrac.toFloat()        * h).coerceIn(0f, h)
    val botY   = (state.towerBottomYFrac.toFloat() * h).coerceIn(0f, h)
    val waterY = (state.waterYFrac.toFloat()       * h).coerceIn(0f, h)

    if (topY >= deckY) return   // 主塔不在畫面內

    // ── 主塔視覺寬度
    //   下限 5px：避免廣角(14mm)時塔細到消失
    //   上限 6% 畫面寬：避免望遠(300mm)時塔變成牆壁；按畫面尺度等比放大，
    //   而非寫死 9px——否則 zoom in 時塔只變高不變寬，視覺上像被「拉長」
    val physW = ((state.towerRightFrac - state.towerLeftFrac).toFloat() * w)
        .coerceAtLeast(5f)
        .coerceAtMost(w * 0.06f)
    val hw    = physW / 2f

    // ── A 字交會點 Y：水面以上 72m / 露出塔總高 200m，線性插值在 topY..deckY 之間
    //   仿透視 (foreshortening) 在常見觀察距離下誤差 <5%，忽略不計
    val towerExposed = (BridgeTower.TOWER_TIP_ELEVATION_M - BridgeTower.DECK_ELEVATION_M).toFloat()
    val aJoinFromTop = ((BridgeTower.TOWER_TIP_ELEVATION_M - BridgeTower.A_FRAME_JOIN_ELEVATION_M) / towerExposed).toFloat()
    val aJoinY = topY + (deckY - topY) * aJoinFromTop

    // ── 視覺下限：當觀察者貼近橋軸 (sinAngle≈0) 時 ViewModel 的 spanFrac 會被壓到 0.04，
    //   讓整段橋面看不出形狀。用「塔高 × 真實水平/垂直比例」推算 spanFrac 下限，
    //   保證即便端視角也能看出八里跨 450m、淡水跨 175m 的結構。
    val towerVisH = (deckY - topY).coerceAtLeast(1f)
    val baliSpanMinFrac    = (towerVisH / w) * (BridgeTower.MAIN_SPAN_M / BridgeTower.TOWER_TIP_ELEVATION_M).toFloat()
    val tamsuilSpanMinFrac = (towerVisH / w) * (BridgeTower.SIDE_SPAN_M / BridgeTower.TOWER_TIP_ELEVATION_M).toFloat()
    val baliSpanFrac    = state.baliSpanFrac.toFloat().coerceAtLeast(baliSpanMinFrac)
    val tamsuilSpanFrac = state.tamsuilSpanFrac.toFloat().coerceAtLeast(tamsuilSpanMinFrac)
    val baliSpanPx = baliSpanFrac * w
    val tamsuiSpanPx = tamsuilSpanFrac * w

    // ── 1. 引橋（主跨/側跨外的橋面延伸）
    drawApproachSpans(
        midX, deckY, w, hw,
        baliIsOnLeft   = state.baliIsOnLeft,
        baliSpanFrac   = baliSpanFrac,
        tamsuilSpanFrac = tamsuilSpanFrac,
    )

    // ── 2. 斜張索：從塔頂錨點散開到橋面，掛點分布於 [0, 最長鋼索水平距離] 範圍內
    drawCableStays(
        midX, topY, deckY, hw,
        baliIsOnLeft  = state.baliIsOnLeft,
        baliSpanFrac  = baliSpanFrac,
        tamsuilSpanFrac = tamsuilSpanFrac,
    )

    // ── 3. 橋面板
    drawBridgeDeck(deckY, w, hw)

    // ── 4. A 字主塔（甲板以下倒 V 基座 + 甲板以上 A 字塔身）
    drawTowerAFrame(midX, topY, aJoinY, deckY, botY, h, hw)

    // ── 5. 水面倒影：水面使用海平面投影，不吃畫面剩餘高度比例，
    //   避免 zoom in 時水面和橋面被不自然拉開。
    //   倒影高度跟著「塔可見高度 (deckY - topY)」走，
    //   而非固定 50% 反射區，這樣 zoom in 時倒影也跟著放大
    drawWaterReflection(midX, topY, deckY, waterY, w, h, hw)
    drawShoreMasks(
        baliX = if (state.baliIsOnLeft) midX - baliSpanPx else midX + baliSpanPx,
        tamsuiX = if (state.baliIsOnLeft) midX + tamsuiSpanPx else midX - tamsuiSpanPx,
        baliIsOnLeft = state.baliIsOnLeft,
        deckY = deckY,
        waterY = waterY,
        hw = hw,
    )
}

/**
 * 斜張索（動態）：
 *   - baliIsOnLeft：八里主跨（450m，14 條）在左或右，由觀察者方位決定
 *   - baliSpanFrac / tamsuilSpanFrac：跨度比例（已在 drawBridge 套用視覺下限）
 *   - 鋼索頂端錨在塔頂 topY 附近（與參考圖相符，斜張索從塔頂扇形散開）
 *   - 鋼索掛點水平距離只到「最長鋼索」處（八里 410/450=91%、淡水 320/175=183%，
 *     淡水端超過側跨會延伸到引橋區），跨距末端到最長鋼索之間是無索區
 */
private fun DrawScope.drawCableStays(
    midX: Float, topY: Float, deckY: Float, hw: Float,
    baliIsOnLeft: Boolean, baliSpanFrac: Float, tamsuilSpanFrac: Float,
) {
    val baliStayRatio    = (BridgeTower.BALI_LONGEST_STAY_M / BridgeTower.MAIN_SPAN_M).toFloat()
    val tamsuilStayRatio = (BridgeTower.TAMSUI_LONGEST_STAY_M / BridgeTower.SIDE_SPAN_M).toFloat()

    val baliMax    = baliSpanFrac    * baliStayRatio    * size.width
    val tamsuilMax = tamsuilSpanFrac * tamsuilStayRatio * size.width

    val leftMax:  Float
    val leftN:    Int
    val rightMax: Float
    val rightN:   Int
    if (baliIsOnLeft) {
        leftMax = baliMax;    leftN = 14
        rightMax = tamsuilMax; rightN = 9
    } else {
        leftMax = tamsuilMax; leftN = 9
        rightMax = baliMax;   rightN = 14
    }

    // 鋼索由塔頂錨點散開（與參考圖一致：斜張索集中於塔頂）
    val anchorY = topY
    // 鋼索粗細跟著 hw 等比例變化：zoom in 時鋼索看起來更明顯
    val stayStrokeWidth = (hw * 0.12f).coerceIn(0.7f, 2.5f)

    // t = (i+1)/N 讓最後一條鋼索剛好掛在指定的最長水平距離（leftMax / rightMax）
    // 左側斜張索
    repeat(leftN) { i ->
        val t = (i + 1).toFloat() / leftN.toFloat()
        val alpha = lerp(0.58f, 0.22f, t)
        drawLine(
            Color.White.copy(alpha = alpha),
            start = Offset(midX - hw * 0.3f, anchorY),
            end   = Offset(midX - leftMax * t, deckY),
            strokeWidth = stayStrokeWidth,
        )
    }
    // 右側斜張索
    repeat(rightN) { i ->
        val t = (i + 1).toFloat() / rightN.toFloat()
        val alpha = lerp(0.58f, 0.22f, t)
        drawLine(
            Color.White.copy(alpha = alpha),
            start = Offset(midX + hw * 0.3f, anchorY),
            end   = Offset(midX + rightMax * t, deckY),
            strokeWidth = stayStrokeWidth,
        )
    }
}

/**
 * 引橋：主跨/側跨之外的延伸橋面，無斜張索，以橋墩柱撐起。
 * 八里端：主跨 450m → 引橋 75 + 75m
 * 淡水端：側跨 175m → 引橋 75 + 70m
 */
private fun DrawScope.drawApproachSpans(
    midX: Float, deckY: Float, w: Float, hw: Float,
    baliIsOnLeft: Boolean, baliSpanFrac: Float, tamsuilSpanFrac: Float,
) {
    val baliApproachRatio    = (BridgeTower.BALI_APPROACH_TOTAL_M / BridgeTower.MAIN_SPAN_M).toFloat()
    val tamsuilApproachRatio = (BridgeTower.TAMSUI_APPROACH_TOTAL_M / BridgeTower.SIDE_SPAN_M).toFloat()

    // 各端引橋的「畫面像素長度」
    val baliApproachPx    = baliSpanFrac    * baliApproachRatio    * w
    val tamsuilApproachPx = tamsuilSpanFrac * tamsuilApproachRatio * w

    // 八里主跨末端、淡水側跨末端的畫面 X 位置（自塔起算）
    val baliSpanPx    = baliSpanFrac    * w
    val tamsuilSpanPx = tamsuilSpanFrac * w

    val (leftSpanPx, leftApproachPx, leftPierFractions) = if (baliIsOnLeft) {
        Triple(baliSpanPx, baliApproachPx, listOf(75f / 150f))    // 八里 75+75 分節
    } else {
        Triple(tamsuilSpanPx, tamsuilApproachPx, listOf(75f / 145f)) // 淡水 75+70 分節
    }
    val (rightSpanPx, rightApproachPx, rightPierFractions) = if (baliIsOnLeft) {
        Triple(tamsuilSpanPx, tamsuilApproachPx, listOf(75f / 145f))
    } else {
        Triple(baliSpanPx, baliApproachPx, listOf(75f / 150f))
    }

    val approachColor   = Color(0xFF1E1E1E).copy(alpha = 0.85f) // 比主橋面略淡，做出區隔
    val pierColor       = Color(0xFF222222)
    // 引橋面比主橋面薄一點；橋墩高度與寬度也跟著 hw 等比例變化
    val approachThick   = (hw * 0.85f).coerceIn(3f, 16f)
    val pierWidth       = (hw * 0.8f).coerceAtLeast(3f)
    val pierHeight      = (hw * 2.5f).coerceAtLeast(10f)

    // 左側引橋
    val leftStartX = (midX - leftSpanPx).coerceAtLeast(0f)
    val leftEndX   = (leftStartX - leftApproachPx).coerceAtLeast(0f)
    if (leftStartX > 0f) {
        drawRect(
            color = approachColor,
            topLeft = Offset(leftEndX, deckY - approachThick / 2f),
            size = Size(leftStartX - leftEndX, approachThick),
        )
        // 橋墩（介於引橋兩段之間）
        leftPierFractions.forEach { f ->
            val px = leftStartX - leftApproachPx * f
            drawRect(
                color = pierColor,
                topLeft = Offset(px - pierWidth / 2f, deckY - approachThick / 2f),
                size = Size(pierWidth, pierHeight),
            )
        }
        // 引橋末端的端墩
        if (leftEndX > 0f) {
            drawRect(
                color = pierColor,
                topLeft = Offset(leftEndX - pierWidth / 2f, deckY - approachThick / 2f),
                size = Size(pierWidth, pierHeight),
            )
        }
    }

    // 右側引橋
    val rightStartX = (midX + rightSpanPx).coerceAtMost(w)
    val rightEndX   = (rightStartX + rightApproachPx).coerceAtMost(w)
    if (rightStartX < w) {
        drawRect(
            color = approachColor,
            topLeft = Offset(rightStartX, deckY - approachThick / 2f),
            size = Size(rightEndX - rightStartX, approachThick),
        )
        rightPierFractions.forEach { f ->
            val px = rightStartX + rightApproachPx * f
            drawRect(
                color = pierColor,
                topLeft = Offset(px - pierWidth / 2f, deckY - approachThick / 2f),
                size = Size(pierWidth, pierHeight),
            )
        }
        if (rightEndX < w) {
            drawRect(
                color = pierColor,
                topLeft = Offset(rightEndX - pierWidth / 2f, deckY - approachThick / 2f),
                size = Size(pierWidth, pierHeight),
            )
        }
    }
}

private fun DrawScope.drawBridgeDeck(deckY: Float, w: Float, hw: Float) {
    // 主橋面厚度跟著 hw（塔半寬）等比例放大；下限 4px、上限 20px 避免極端值
    val thickness = (hw * 1.1f).coerceIn(4f, 20f)
    val half = thickness / 2f
    drawRect(
        color = Color(0xFF1E1E1E),
        topLeft = Offset(0f, deckY - half),
        size = Size(w, thickness),
    )
    // 橋面頂緣高光
    drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, deckY - half), Offset(w, deckY - half), 1f)
    // 橋面底緣陰影
    drawLine(Color.Black.copy(alpha = 0.35f), Offset(0f, deckY + half), Offset(w, deckY + half), 1f)
}

/**
 * A 字主塔：
 *   - 甲板以上：在 A 字交會點 aJoinY 上方為「單柱」、下方為「兩柱張開」直到橋面 deckY
 *   - 甲板以下：倒 V 基座兩腳張開（真實塔基在水下 EL -11.4m，畫面通常看不到，但用 botY 撐住視覺）
 *
 * A 字結構參數：
 *   交會點高度 72m / 露出塔高 200m，所以 aJoinY 在 topY..deckY 之間距 topY 約 64%
 *   下半張開幅度：A 字柱腳在橋面寬度約塔身寬 4×（視覺比例，比真實略誇張以利辨識）
 */
private fun DrawScope.drawTowerAFrame(
    midX: Float, topY: Float, aJoinY: Float, deckY: Float, botY: Float, h: Float, hw: Float,
) {
    val tw = hw * 2f

    val towerColor   = Color(0xFF141414)
    val highlight    = Color.White.copy(alpha = 0.10f)
    val shadow       = Color.Black.copy(alpha = 0.20f)
    val deepShadow   = Color(0xFF0A0A0A)

    // ── 1. 單柱段（aJoinY 之上到 topY）
    val singleH = aJoinY - topY
    if (singleH > 0f) {
        drawRect(towerColor,  Offset(midX - hw, topY), Size(tw, singleH))
        drawRect(highlight,   Offset(midX - hw, topY), Size(hw * 0.4f, singleH))
        drawRect(shadow,      Offset(midX + hw * 0.6f, topY), Size(hw * 0.4f, singleH))
        // 塔頂小帽
        drawRect(deepShadow,  Offset(midX - hw * 0.7f, topY - hw * 1.5f), Size(tw * 0.7f, hw * 1.5f))
    }

    // ── 2. A 字下半（aJoinY 到 deckY）：兩柱張開
    //   兩柱在 aJoinY 處與單柱無縫銜接（左柱占 [midX-hw, midX]、右柱占 [midX, midX+hw]）
    //   兩柱在 deckY 處張開到 midX ± deckSpread
    val deckSpread = hw * 3.5f                  // 柱腳到中心軸距離
    val legBottomW = hw * 1.0f                  // 柱腳寬度
    val aFrameH    = deckY - aJoinY
    if (aFrameH > 0f) {
        // 左柱（梯形：頂左 → 頂中 → 底中 → 底左）
        drawPath(
            Path().apply {
                moveTo(midX - hw, aJoinY)
                lineTo(midX, aJoinY)
                lineTo(midX - deckSpread + legBottomW, deckY)
                lineTo(midX - deckSpread, deckY)
                close()
            },
            towerColor,
        )
        // 左柱高光（外緣）
        drawLine(
            highlight,
            start = Offset(midX - hw, aJoinY),
            end   = Offset(midX - deckSpread, deckY),
            strokeWidth = 1.4f,
        )
        // 右柱
        drawPath(
            Path().apply {
                moveTo(midX, aJoinY)
                lineTo(midX + hw, aJoinY)
                lineTo(midX + deckSpread, deckY)
                lineTo(midX + deckSpread - legBottomW, deckY)
                close()
            },
            towerColor,
        )
        drawLine(
            shadow,
            start = Offset(midX + hw, aJoinY),
            end   = Offset(midX + deckSpread, deckY),
            strokeWidth = 1.4f,
        )
        // 在 aJoinY 處畫一道細橫樑表示「合體」(視覺輔助線)
        drawLine(
            deepShadow.copy(alpha = 0.6f),
            start = Offset(midX - hw - 0.5f, aJoinY),
            end   = Offset(midX + hw + 0.5f, aJoinY),
            strokeWidth = 1.5f,
        )
    }

    // ── 3. 倒 V 基座（甲板以下）：從 deck 兩腳張開繼續往下
    val legLen    = (botY - deckY).coerceAtLeast(h * 0.08f)
    val baseSpread = hw * 4.5f
    val baseLegW   = hw * 0.85f

    // 左腳：從 A 字下端的左柱腳延伸到水下基座
    drawPath(
        Path().apply {
            moveTo(midX - deckSpread, deckY)
            lineTo(midX - baseSpread, deckY + legLen)
            lineTo(midX - baseSpread + baseLegW, deckY + legLen)
            lineTo(midX - deckSpread + legBottomW, deckY)
            close()
        },
        Color(0xFF181818),
    )
    // 右腳
    drawPath(
        Path().apply {
            moveTo(midX + deckSpread, deckY)
            lineTo(midX + baseSpread, deckY + legLen)
            lineTo(midX + baseSpread - baseLegW, deckY + legLen)
            lineTo(midX + deckSpread - legBottomW, deckY)
            close()
        },
        Color(0xFF181818),
    )
}

/** 水面倒影：模糊的倒立塔柱 + 水平漣漪線。倒影高度跟著實際塔可見高度走。 */
private fun DrawScope.drawWaterReflection(
    midX: Float, topY: Float, deckY: Float, waterY: Float, w: Float, h: Float, hw: Float,
) {
    // 水面是世界座標的海平面投影；若被畫面裁切，保留至少一點橋下空間給陰影。
    val waterStart = projectedWaterStart(deckY, waterY, hw)
    if (waterStart >= h) return
    val waterRegion = h - waterStart

    // 水面色塊（漸層）
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF0D2A40).copy(alpha = 0.55f), Color.Transparent),
            startY = waterStart, endY = h,
        ),
        topLeft = Offset(0f, waterStart),
        size    = Size(w, waterRegion),
    )
    // 倒映塔：高度 = 塔在水面上的可見高度，但被畫面底端截斷
    val towerVisH = (deckY - topY).coerceAtLeast(0f)
    val reflTowerH = towerVisH.coerceAtMost(h - waterStart)
    if (reflTowerH > 0f) {
        drawRect(
            Color(0xFF141414).copy(alpha = 0.30f),
            Offset(midX - hw, waterStart),
            Size(hw * 2f, reflTowerH),
        )
    }
    // 漣漪：在倒影區內等比分佈；rippleSpan 隨倒影高度縮放，zoom in 時漣漪也跟著放大
    val rippleSpan = (reflTowerH.coerceAtLeast(waterRegion * 0.3f)).coerceAtMost(waterRegion)
    val rippleStrokeW = (hw * 0.10f).coerceIn(0.5f, 1.6f)
    listOf(0.15f, 0.40f, 0.70f).forEach { t ->
        val y     = waterStart + rippleSpan * t
        val alpha = lerp(0.22f, 0.06f, t)
        val len   = w * lerp(0.35f, 0.85f, t)
        drawLine(Color.White.copy(alpha = alpha), Offset((w - len) / 2f, y), Offset((w + len) / 2f, y), rippleStrokeW)
    }
}

/** 端點外側已靠岸，不再畫水面；用黑色陸地遮住水域。 */
private fun DrawScope.drawShoreMasks(
    baliX: Float,
    tamsuiX: Float,
    baliIsOnLeft: Boolean,
    deckY: Float,
    waterY: Float,
    hw: Float,
) {
    val waterStart = projectedWaterStart(deckY, waterY, hw)
    if (waterStart <= deckY) return

    val shoreColor = Color.Black.copy(alpha = 0.92f)
    val shoreHeight = waterStart - deckY
    fun fillLeft(untilX: Float) {
        val x = untilX.coerceIn(0f, size.width)
        if (x > 0f) {
            drawRect(shoreColor, topLeft = Offset(0f, deckY), size = Size(x, shoreHeight))
        }
    }
    fun fillRight(fromX: Float) {
        val x = fromX.coerceIn(0f, size.width)
        if (x < size.width) {
            drawRect(shoreColor, topLeft = Offset(x, deckY), size = Size(size.width - x, shoreHeight))
        }
    }

    if (baliIsOnLeft) {
        fillLeft(baliX)
        fillRight(tamsuiX)
    } else {
        fillRight(baliX)
        fillLeft(tamsuiX)
    }
}

private fun projectedWaterStart(deckY: Float, waterY: Float, hw: Float): Float {
    val minVisibleGap = (hw * 1.6f).coerceIn(4f, 18f)
    return waterY.coerceAtLeast(deckY + minVisibleGap)
}

/** 在畫面角落疊加橋樑標示文字（左右依方位動態翻轉）*/
private fun DrawScope.drawBridgeLabels(
    state: FocalSimulatorState,
    w: Float,
    h: Float,
    baliLabel: String,
    tamsuiLabel: String,
    towerHeightLabel: String,
) {
    val deckY = (state.deckYFrac.toFloat() * h).coerceIn(0f, h)
    if (state.towerTopYFrac >= state.deckYFrac) return   // 塔不在畫面

    val paint = Paint().apply {
        isAntiAlias = true
        typeface    = Typeface.DEFAULT
        textSize    = 26f
        color       = android.graphics.Color.argb(175, 255, 255, 255)
    }
    val nc = drawContext.canvas.nativeCanvas

    // 八里 / 淡水依 baliIsOnLeft 決定在左或右
    val (leftLabel, rightLabel) = if (state.baliIsOnLeft) {
        "← $baliLabel" to "$tamsuiLabel →"
    } else {
        "← $tamsuiLabel" to "$baliLabel →"
    }

    nc.drawText(leftLabel, 12f, deckY - 12f, paint)
    val rw = paint.measureText(rightLabel)
    nc.drawText(rightLabel, w - rw - 12f, deckY - 12f, paint)

    // 塔身標示
    val topY = (state.towerTopYFrac.toFloat() * h).coerceIn(0f, h)
    if (topY < deckY - 24f) {
        paint.textSize = 22f
        paint.color    = android.graphics.Color.argb(155, 255, 215, 90)
        nc.drawText(towerHeightLabel, w / 2f + 14f, topY + 24f, paint)
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

// ── 時間滑桿的非線性映射 ───────────────────────────────────────────────
//   起點固定 12:00；終點動態 = 日落 + 30 分鐘。三段式，越接近日落越細：
//     粗調區 [12:00, 日落-90m]            佔 COARSE_TRACK_FRACTION
//     細調區 [日落-90m, 日落-10m]          佔 FINE_TRACK_FRACTION
//     超細區 [日落-10m, 日落+10m]          佔剩餘軌道（最細）
private const val TIME_MIN_MINUTE = 12 * 60          // 720 = 12:00
private const val FALLBACK_SUNSET_MINUTE = 17 * 60 + 30
private const val END_AFTER_SUNSET_MINUTE = 10       // 終點 = 日落 + 10 分鐘
private const val FINE_WINDOW_MINUTE = 90            // 日落前 90 分鐘進入細調區
private const val ULTRA_WINDOW_MINUTE = 10           // 日落前 10 分鐘進入超細區
private const val COARSE_TRACK_FRACTION = 0.30f      // 粗調區佔軌道
private const val FINE_TRACK_FRACTION = 0.35f        // 細調區佔軌道（超細區 = 0.35）

/** 滑桿各分界（分鐘），由日落時間動態推算並保證遞增。 */
private data class TimeSliderBounds(
    val minMinute: Int,
    val fineStart: Int,
    val ultraStart: Int,
    val maxMinute: Int,
)

private fun timeSliderBounds(sunsetMinute: Int?): TimeSliderBounds {
    val sunset = sunsetMinute ?: FALLBACK_SUNSET_MINUTE
    val min = TIME_MIN_MINUTE
    val end = sunset + END_AFTER_SUNSET_MINUTE
    val ultraStart = (sunset - ULTRA_WINDOW_MINUTE).coerceIn(min + 2, end - 1)
    val fineStart = (sunset - FINE_WINDOW_MINUTE).coerceIn(min + 1, ultraStart - 1)
    return TimeSliderBounds(min, fineStart, ultraStart, end)
}

/** 分鐘 → 滑桿 0..1（三段式，越接近日落越細）。 */
private fun minuteToSliderFraction(minute: Int, sunsetMinute: Int?): Float {
    val b = timeSliderBounds(sunsetMinute)
    val m = minute.coerceIn(b.minMinute, b.maxMinute)
    val coarse = COARSE_TRACK_FRACTION
    val fine = FINE_TRACK_FRACTION
    return when {
        m <= b.fineStart ->
            coarse * (m - b.minMinute).toFloat() / (b.fineStart - b.minMinute).toFloat()
        m <= b.ultraStart ->
            coarse + fine * (m - b.fineStart).toFloat() / (b.ultraStart - b.fineStart).toFloat()
        else ->
            coarse + fine + (1f - coarse - fine) *
                (m - b.ultraStart).toFloat() / (b.maxMinute - b.ultraStart).toFloat()
    }
}

/** 滑桿 0..1 → 分鐘。 */
private fun sliderFractionToMinute(fraction: Float, sunsetMinute: Int?): Int {
    val b = timeSliderBounds(sunsetMinute)
    val f = fraction.coerceIn(0f, 1f)
    val coarse = COARSE_TRACK_FRACTION
    val fine = FINE_TRACK_FRACTION
    val minute = when {
        f <= coarse ->
            b.minMinute + (b.fineStart - b.minMinute) * (f / coarse)
        f <= coarse + fine ->
            b.fineStart + (b.ultraStart - b.fineStart) * ((f - coarse) / fine)
        else ->
            b.ultraStart + (b.maxMinute - b.ultraStart) *
                ((f - coarse - fine) / (1f - coarse - fine))
    }
    return minute.toInt()
}

private fun DrawScope.drawSunTrail(state: FocalSimulatorState, w: Float, h: Float) {
    state.sunTrail.forEach { p ->
        if (!p.inFrame) return@forEach
        val x = (p.xFrac.toFloat() * w)
        val y = (p.yFrac.toFloat() * h)
        val isCurrent = p.timeOffsetMinutes == 0
        val color = when {
            isCurrent -> Color(0xFFFFD66B)
            p.timeOffsetMinutes < 0 -> Color(0xFFFFD66B).copy(alpha = 0.25f)
            else -> Color(0xFFFFD66B).copy(alpha = 0.45f)
        }
        drawCircle(color = color, radius = 4f, center = Offset(x, y))
    }
}

private fun DrawScope.drawSun(state: FocalSimulatorState, w: Float, h: Float) {
    val sun = state.sun
    if (!sun.inFrame) return
    val x = (sun.xFrac.toFloat() * w).coerceIn(0f, w)
    val y = (sun.yFrac.toFloat() * h).coerceIn(0f, h)
    val radiusPx = (state.sunRadiusFrac.toFloat() * w).coerceAtLeast(3f)
    // 光暈
    drawCircle(color = Color(0xFFFFD66B).copy(alpha = 0.35f), radius = radiusPx * 2.2f, center = Offset(x, y))
    drawCircle(color = Color(0xFFFFD66B).copy(alpha = 0.55f), radius = radiusPx * 1.5f, center = Offset(x, y))
    drawCircle(color = Color(0xFFFFE19A), radius = radiusPx, center = Offset(x, y))
    drawCircle(
        color = Color.White.copy(alpha = 0.7f),
        radius = radiusPx,
        center = Offset(x, y),
        style = Stroke(width = 1.2f),
    )
}

private fun DrawScope.drawOffFrameArrows(sun: SunFramePosition, w: Float, h: Float) {
    if (sun.inFrame) return
    val arrowColor = Color(0xFFFFD66B)
    val arrowSize = 18f
    when {
        sun.offFrameLeft -> drawArrow(Offset(arrowSize, h / 2f), Math.PI.toFloat(), arrowSize, arrowColor)
        sun.offFrameRight -> drawArrow(Offset(w - arrowSize, h / 2f), 0f, arrowSize, arrowColor)
        sun.offFrameTop -> drawArrow(Offset(w / 2f, arrowSize), (-Math.PI / 2).toFloat(), arrowSize, arrowColor)
        sun.offFrameBottom -> drawArrow(Offset(w / 2f, h - arrowSize), (Math.PI / 2).toFloat(), arrowSize, arrowColor)
    }
}

private fun DrawScope.drawArrow(tip: Offset, angleRad: Float, size: Float, color: Color) {
    val cos = kotlin.math.cos(angleRad)
    val sin = kotlin.math.sin(angleRad)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - size * cos - size * 0.6f * sin, tip.y - size * sin + size * 0.6f * cos)
        lineTo(tip.x - size * cos + size * 0.6f * sin, tip.y - size * sin - size * 0.6f * cos)
        close()
    }
    drawPath(path = path, color = color)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SensorPicker(current: SensorSpec, onPick: (SensorSpec) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = stringResource(current.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.focal_sensor_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SensorSpec.ALL.forEach { spec ->
                DropdownMenuItem(
                    text = { Text(stringResource(spec.labelRes)) },
                    onClick = {
                        onPick(spec)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HotspotPicker(
    current: Hotspot,
    hotspots: List<Hotspot>,
    gpsReady: Boolean,
    onPick: (Hotspot) -> Unit,
    onRequestGps: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val displayName = when {
        current.id == GPS_HOTSPOT_ID -> stringResource(R.string.gps_hotspot_name)
        current.nameRes != null -> stringResource(current.nameRes!!)
        else -> current.customName.orEmpty()
    }

    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(displayName) },
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // ── 第一項：GPS 目前位置（永遠顯示；沒 GPS 時呼叫 onRequestGps 觸發權限/重試）
            val gpsLabel = if (gpsReady) stringResource(R.string.gps_hotspot_name) else stringResource(R.string.gps_hotspot_enable)
            DropdownMenuItem(
                text = { Text(gpsLabel) },
                onClick = {
                    if (gpsReady) onPick(GPS_HOTSPOT) else onRequestGps()
                    expanded = false
                },
            )
            androidx.compose.material3.HorizontalDivider()
            // ── 合併後熱點清單（含自訂）────────────────────────────
            hotspots.forEach { h ->
                val name = h.nameRes?.let { ctx.getString(it) } ?: h.customName.orEmpty()
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onPick(h); expanded = false },
                )
            }
        }
    }
}
