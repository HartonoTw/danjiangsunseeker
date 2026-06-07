package studio.freestyle.labs.danjiangsunseeker.presentation.ar

import android.Manifest
import android.content.pm.PackageManager
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import studio.freestyle.labs.danjiangsunseeker.R
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun ARScreen(vm: ARViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val view = LocalView.current

    val state by vm.state.collectAsState()

    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permissionsGranted =
            (results[Manifest.permission.CAMERA] == true) &&
                (results[Manifest.permission.ACCESS_FINE_LOCATION] == true)
    }

    if (!permissionsGranted) {
        PermissionPrompt(onRequest = {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        })
        return
    }

    // 螢幕旋轉
    DisposableEffect(view) {
        @Suppress("DEPRECATION")
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        vm.setDisplayRotation(rotation)
        onDispose { /* no-op */ }
    }

    // 啟動 / 停止感測器
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }

    // 持有 Camera 物件以便控制曝光
    var camera by remember { mutableStateOf<Camera?>(null) }

    // 校正模式時降低曝光以保護眼睛；退出後復原
    LaunchedEffect(state.calibrating, camera) {
        val c = camera ?: return@LaunchedEffect
        val range = c.cameraInfo.exposureState.exposureCompensationRange
        val target = if (state.calibrating) range.lower else 0
        runCatching { c.cameraControl.setExposureCompensationIndex(target) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            active = !state.cameraBlockedBySun,
            onFovComputed = { vm.setCameraFov(it) },
            onCameraReady = { camera = it },
        )

        if (state.cameraBlockedBySun) {
            // 鏡頭太接近太陽：以暗色蓋掉相機畫面（相機已停止擷取），
            // 但保留日落投影（太陽軌跡 / 日落標記 / 主塔）讓使用者仍能取景對位。
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF120A06)))
            AROverlay(state, modifier = Modifier.fillMaxSize())
            InfoHud(
                state,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
            SunSafetyBanner(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            )
        } else {
            AROverlay(state, modifier = Modifier.fillMaxSize())
            InfoHud(
                state,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )

            if (!state.calibrating) {
                // 校正鈕：僅在日落前 45 分鐘內可用；其餘時段點擊顯示提示
                FloatingActionButton(
                    onClick = {
                        if (state.calibrationAllowed) {
                            vm.startCalibration()
                        } else {
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.ar_calibration_window_hint),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .size(52.dp),
                    containerColor = if (state.calibrationAllowed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    },
                ) {
                    Icon(Icons.Outlined.GpsFixed, contentDescription = stringResource(R.string.cd_calibrate))
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 月亮轉向指引（付費功能）：與太陽一樣告訴使用者要往哪轉幾度才能找到月亮軌跡
                    MoonGuidanceBadge(state)
                    AlignmentBadge(
                        state,
                        onReset = { vm.resetCalibration() },
                        modifier = Modifier,
                    )
                }
            } else {
                CalibrationOverlay(
                    onConfirm = { vm.confirmCalibration() },
                    onCancel = { vm.cancelCalibration() },
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.ar_permission_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ar_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.action_grant_permission)) }
    }
}

/** 鏡頭因太接近太陽暫停時的護眼/護鏡頭警告橫幅（疊在日落投影之上）。 */
@Composable
private fun SunSafetyBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.94f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.ar_sun_shutter_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.ar_sun_shutter_body),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AROverlay(state: ARState, modifier: Modifier) {
    Canvas(modifier = modifier) {
        if (!state.ready) return@Canvas
        val w = size.width
        val h = size.height

        // 太陽軌跡 — 距日落時刻越近，點越大越亮
        state.sunTrajectory.forEach { t ->
            if (!t.inFrame) return@forEach
            val absFromSunset = t.minutesFromSunset
            val hasSunset = !absFromSunset.isNaN()
            val (radius, alpha) = if (hasSunset) {
                // 0 分→ r=8, α=1.0；30 分→ r=3, α=0.25；60 分+→ r=2, α=0.15
                val nearness = (1.0 / (1.0 + absFromSunset / 8.0))
                val r = (2.5 + 8.0 * nearness).toFloat()
                val a = (0.15 + 0.85 * nearness).toFloat().coerceIn(0.15f, 1.0f)
                r to a
            } else 4f to 0.4f

            val isFuture = t.timeOffsetMinutes >= 0
            val baseColor = if (isFuture) Color(0xFFFFD66B) else Color(0xFFFFA640)
            drawCircle(
                color = baseColor.copy(alpha = alpha),
                radius = radius,
                center = Offset((t.xFrac * w).toFloat(), (t.yFrac * h).toFloat()),
            )
        }

        // 月亮軌跡 (付費功能；銀藍色，與太陽暖色區隔)
        if (state.premiumUnlocked) {
            state.moonTrajectory.forEach { t ->
                if (!t.inFrame) return@forEach
                val isFuture = t.timeOffsetMinutes >= 0
                val alpha = if (isFuture) 0.55f else 0.3f
                drawCircle(
                    color = Color(0xFFCAD6FF).copy(alpha = alpha),
                    radius = 3.5f,
                    center = Offset((t.xFrac * w).toFloat(), (t.yFrac * h).toFloat()),
                )
            }
        }

        // 日落瞬間特別標記 (紅色十字 + 圓圈)
        state.sunsetMarker?.takeIf { it.inFrame }?.let { m ->
            val cx = (m.xFrac * w).toFloat()
            val cy = (m.yFrac * h).toFloat()
            val ringColor = Color(0xFFFF6B6B)
            drawCircle(ringColor, radius = 18f, center = Offset(cx, cy), style = Stroke(width = 3f))
            drawCircle(ringColor.copy(alpha = 0.35f), radius = 30f, center = Offset(cx, cy), style = Stroke(width = 2f))
            drawLine(ringColor, Offset(cx - 26f, cy), Offset(cx - 18f, cy), strokeWidth = 2f)
            drawLine(ringColor, Offset(cx + 18f, cy), Offset(cx + 26f, cy), strokeWidth = 2f)
            drawLine(ringColor, Offset(cx, cy - 26f), Offset(cx, cy - 18f), strokeWidth = 2f)
            drawLine(ringColor, Offset(cx, cy + 18f), Offset(cx, cy + 26f), strokeWidth = 2f)
        }

        // 主塔虛擬輪廓 (頂點 - 中點 - 底點豎直線 + 上下圓圈)
        if (state.towerTip.inFrame || state.towerBase.inFrame || state.tower.inFrame) {
            val tipY = (state.towerTip.yFrac * h).toFloat()
            val baseY = (state.towerBase.yFrac * h).toFloat()
            val midX = (state.tower.xFrac * w).toFloat()
            drawLine(
                color = Color(0xFFE63946),
                start = Offset(midX, tipY.coerceIn(0f, h)),
                end = Offset(midX, baseY.coerceIn(0f, h)),
                strokeWidth = 6f,
            )
            drawCircle(Color(0xFFE63946), 10f, Offset(midX, tipY.coerceIn(0f, h)))
            drawCircle(Color(0xFFE63946), 8f, Offset(midX, baseY.coerceIn(0f, h)))
        }

        // 太陽 (圓 + 光暈)
        if (state.sun.inFrame) {
            val cx = (state.sun.xFrac * w).toFloat()
            val cy = (state.sun.yFrac * h).toFloat()
            drawCircle(Color(0xFFFFD66B).copy(alpha = 0.35f), 50f, Offset(cx, cy))
            drawCircle(Color(0xFFFFD66B).copy(alpha = 0.55f), 32f, Offset(cx, cy))
            drawCircle(Color(0xFFFFE19A), 20f, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.7f), 20f, Offset(cx, cy), style = Stroke(width = 1.5f))
        }

        // 月亮 (付費功能)：在框內畫弦月圖案；不在框內則於邊緣畫弦月標記 + 箭頭指引方位
        if (state.premiumUnlocked) {
            if (state.moon.inFrame) {
                val cx = (state.moon.xFrac * w).toFloat()
                val cy = (state.moon.yFrac * h).toFloat()
                drawCircle(Color(0xFFCAD6FF).copy(alpha = 0.30f), 34f, Offset(cx, cy))
                drawMoonGlyph(Offset(cx, cy), 16f)
            } else {
                drawMoonEdgeIndicator(state.moon, w, h)
            }
        }

        // 主塔出框箭頭
        drawOffFrameArrow(state.tower, Color(0xFFE63946), w, h)
        // 太陽出框箭頭
        drawOffFrameArrow(state.sun, Color(0xFFFFD66B), w, h)
        // 日落位置出框箭頭 (引導使用者轉向太陽軌跡所在方向)
        state.sunsetMarker?.let { drawOffFrameArrow(it, Color(0xFFFF6B6B), w, h) }
    }
}

/** 弦月圖案：銀白圓盤 + 裁切於圓內的暗影偏移，做出新月/弦月的辨識度（與太陽的暖黃光暈區隔）。 */
private fun DrawScope.drawMoonGlyph(center: Offset, radius: Float) {
    drawCircle(Color(0xFFEDF0FA), radius, center)
    clipPath(Path().apply { addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)) }) {
        drawCircle(Color(0xFF2A2F45).copy(alpha = 0.6f), radius, center.copy(x = center.x + radius * 0.5f))
    }
    drawCircle(Color.White.copy(alpha = 0.7f), radius, center, style = Stroke(width = 1.2f))
}

/** 月亮不在畫面內時：於邊緣畫弦月標記 + 箭頭，指引使用者往月亮方位轉。 */
private fun DrawScope.drawMoonEdgeIndicator(target: ARTarget, w: Float, h: Float) {
    if (target.inFrame) return
    val silver = Color(0xFFCAD6FF)
    val inset = 40f
    val anchor = when {
        target.offFrameLeft -> Offset(inset, h / 2f)
        target.offFrameRight -> Offset(w - inset, h / 2f)
        target.offFrameTop -> Offset(w / 2f, inset)
        target.offFrameBottom -> Offset(w / 2f, h - inset)
        else -> return
    }
    drawMoonGlyph(anchor, 14f)
    drawOffFrameArrow(target, silver, w, h)
}

private fun DrawScope.drawOffFrameArrow(target: ARTarget, color: Color, w: Float, h: Float) {
    if (target.inFrame) return
    val size = 22f
    val (tip, angle) = when {
        target.offFrameLeft -> Offset(size, h / 2f) to Math.PI.toFloat()
        target.offFrameRight -> Offset(w - size, h / 2f) to 0f
        target.offFrameTop -> Offset(w / 2f, size) to (-Math.PI / 2).toFloat()
        target.offFrameBottom -> Offset(w / 2f, h - size) to (Math.PI / 2).toFloat()
        else -> return
    }
    val cos = kotlin.math.cos(angle)
    val sin = kotlin.math.sin(angle)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - size * cos - size * 0.6f * sin, tip.y - size * sin + size * 0.6f * cos)
        lineTo(tip.x - size * cos + size * 0.6f * sin, tip.y - size * sin - size * 0.6f * cos)
        close()
    }
    drawPath(path = path, color = color)
}

@Composable
private fun CalibrationOverlay(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    // 5 秒倒數安全限制 — 鼓勵使用者快速完成
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (elapsedSeconds < 30) {
            delay(1000)
            elapsedSeconds += 1
        }
    }
    val timeRemaining = (5 - elapsedSeconds).coerceAtLeast(0)

    Box(modifier = Modifier.fillMaxSize()) {
        // 中央十字準星
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val len = 40f
            val gap = 12f
            val color = Color(0xFFFFD66B)
            drawCircle(color = color.copy(alpha = 0.5f), radius = 36f, center = Offset(cx, cy), style = Stroke(width = 2f))
            drawCircle(color = color, radius = 4f, center = Offset(cx, cy))
            drawLine(color, Offset(cx - len - gap, cy), Offset(cx - gap, cy), strokeWidth = 3f)
            drawLine(color, Offset(cx + gap, cy), Offset(cx + len + gap, cy), strokeWidth = 3f)
            drawLine(color, Offset(cx, cy - len - gap), Offset(cx, cy - gap), strokeWidth = 3f)
            drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + len + gap), strokeWidth = 3f)
        }

        // 頂部：安全警告
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.92f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.ar_safety_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.ar_safety_body, timeRemaining),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // 底部：說明 + 操作按鈕
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.ar_calib_aim_title),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.ar_calib_aim_body),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                ) {
                    Icon(Icons.Outlined.GpsFixed, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ar_calib_confirm))
                }
            }
            if (timeRemaining == 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.ar_calib_timeout_warning),
                    color = Color(0xFFFFCDD2),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun InfoHud(state: ARState, modifier: Modifier) {
    val accuracy = state.locationAccuracyMeters
    Card(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                "📍 ${state.observer?.let { "%.5f, %.5f".format(it.latitude, it.longitude) } ?: stringResource(R.string.ar_hud_searching_location)}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            accuracy?.let {
                Text(
                    stringResource(R.string.ar_hud_accuracy, it.toInt(), "%.1f".format(state.orientation.declinationDegrees)),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                stringResource(
                    R.string.ar_hud_orientation,
                    "%.1f".format(state.orientation.trueAzimuthDegrees),
                    "%.1f".format(state.orientation.pitchDegrees),
                ),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.distanceToTowerKm > 0) {
                Text(
                    stringResource(
                        R.string.ar_hud_tower,
                        "%.2f".format(state.distanceToTowerKm),
                        "%.1f".format(state.bearingToTowerDegrees),
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(
                        R.string.ar_hud_sun,
                        "%.1f".format(state.sunAzimuthDegrees),
                        "%+.1f".format(state.sunAltitudeDegrees),
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
                // 月亮 (付費功能)：與太陽並列顯示，讓使用者同時看到日月位置
                if (state.premiumUnlocked) {
                    Text(
                        stringResource(
                            R.string.ar_hud_moon,
                            "%.1f".format(state.moonAzimuthDegrees),
                            "%+.1f".format(state.moonAltitudeDegrees),
                        ),
                        color = Color(0xFFCAD6FF),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (state.calibrationApplied) {
                Text(
                    stringResource(
                        R.string.ar_hud_calibrated,
                        "%+.1f".format(state.calibrationAzimuthOffsetDeg),
                        "%+.1f".format(state.calibrationPitchOffsetDeg),
                    ),
                    color = Color(0xFF8FE0A2),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            state.errorMessage?.let {
                Text("⚠ $it", color = Color(0xFFFFA0A0), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * 月亮轉向指引（付費功能）：月亮不在畫面內時，於下方顯示「向左/右轉 X° 找月亮軌跡」，
 * 與太陽找日落軌跡的提示對應（銀藍色，與太陽暖色區隔）。
 *
 * 與太陽一致：只要月亮不在框內就持續指引（不限月亮須在地平線上）——AR 的月亮軌跡涵蓋
 * now−1h ~ now+5h，即使此刻月亮在地平線下，依方位轉向仍能找到其升起/行經的軌跡。
 */
@Composable
private fun MoonGuidanceBadge(state: ARState) {
    // 鎖定、未就緒、或月亮已在框內 → 不顯示指引
    if (!state.premiumUnlocked || !state.ready) return
    if (state.moon.inFrame) return

    val hOff = state.moon.horizontalOffsetDegrees
    val direction = when {
        hOff > 0 -> stringResource(R.string.ar_turn_right, "%.0f".format(hOff))
        hOff < 0 -> stringResource(R.string.ar_turn_left, "%.0f".format(-hOff))
        else -> stringResource(R.string.ar_facing)
    }
    val moonAzHint = stringResource(R.string.ar_moon_azimuth_hint, "%.0f".format(state.moonAzimuthDegrees))
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.ar_find_moon_trail, direction, moonAzHint),
            color = Color(0xFFCAD6FF),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AlignmentBadge(state: ARState, onReset: () -> Unit = {}, modifier: Modifier) {
    if (!state.ready) {
        // 等待 GPS / FOV 時顯示提示
        val waitingMsg = when {
            state.observer == null -> stringResource(R.string.ar_waiting_gps)
            state.cameraFov == null -> stringResource(R.string.ar_waiting_fov)
            else -> stringResource(R.string.ar_computing)
        }
        Box(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(waitingMsg, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val sunsetMarker = state.sunsetMarker
    val (label: String, color) = when {
        // 日落瞬間還沒進畫面 — 引導使用者轉向
        sunsetMarker != null && !sunsetMarker.inFrame -> {
            val hOff = sunsetMarker.horizontalOffsetDegrees
            val direction = when {
                hOff > 0 -> stringResource(R.string.ar_turn_right, "%.0f".format(hOff))
                hOff < 0 -> stringResource(R.string.ar_turn_left, "%.0f".format(-hOff))
                else -> stringResource(R.string.ar_facing)
            }
            val sunsetAz = state.sunsetAzimuthDegrees?.let { stringResource(R.string.ar_sunset_azimuth_hint, "%.0f".format(it)) } ?: ""
            stringResource(R.string.ar_find_sun_trail, direction, sunsetAz) to Color(0xFFFFC270)
        }
        abs(state.alignmentOffsetDegrees) < 0.5 ->
            stringResource(R.string.ar_almost_aligned, "%+.2f".format(state.alignmentOffsetDegrees)) to Color(0xFF6FCF97)
        abs(state.alignmentOffsetDegrees) < 2.0 ->
            stringResource(R.string.ar_near_golden, "%+.2f".format(state.alignmentOffsetDegrees)) to Color(0xFFFFA640)
        abs(state.alignmentOffsetDegrees) < 10 ->
            stringResource(R.string.ar_sun_off_minor, "%+.1f".format(state.alignmentOffsetDegrees)) to Color.White
        else ->
            stringResource(R.string.ar_sun_off_major, "%.1f".format(state.alignmentOffsetDegrees)) to Color.LightGray
    }
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
        if (state.calibrationApplied) {
            IconButton(onClick = onReset, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Cancel,
                    contentDescription = stringResource(R.string.cd_clear_calibration),
                    tint = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}
