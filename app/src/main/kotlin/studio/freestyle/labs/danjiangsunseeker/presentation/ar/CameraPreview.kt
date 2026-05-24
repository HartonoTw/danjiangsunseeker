package studio.freestyle.labs.danjiangsunseeker.presentation.ar

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.SizeF
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.atan

/**
 * CameraX 預覽包成 Compose。回呼把實測 FOV 傳回給上層。
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFovComputed: (CameraFov) -> Unit = {},
    onCameraReady: (Camera) -> Unit = {},
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 計算 FOV 一次；失敗時用預設值 (避免極端機型卡住)
    LaunchedEffect(Unit) {
        val fov = runCatching { computeBackCameraFov(ctx) }
            .getOrElse { CameraFov.DEFAULT }
        onFovComputed(fov)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).also { previewView ->
                previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }
                    cameraProvider.unbindAll()
                    runCatching {
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                        )
                        onCameraReady(camera)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        },
    )
}

/**
 * 用 Camera2 [CameraCharacteristics] 算出主後鏡頭的水平 / 垂直 FOV (degrees)。
 *
 * 公式: FOV = 2 × atan( sensorSize / (2 × focalLength) )
 */
data class CameraFov(
    val horizontalDeg: Double,
    val verticalDeg: Double,
    val focalLengthMm: Float,
    val sensorWidthMm: Float,
    val sensorHeightMm: Float,
) {
    companion object {
        /** 多數安卓手機後鏡頭的典型值 (~65° H FOV)。失敗 fallback 與初始預設都用這個。 */
        val DEFAULT = CameraFov(
            horizontalDeg = 65.0,
            verticalDeg = 50.0,
            focalLengthMm = 4.0f,
            sensorWidthMm = 5.7f,
            sensorHeightMm = 4.3f,
        )
    }
}

private fun computeBackCameraFov(context: Context): CameraFov {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val backCameraId = manager.cameraIdList.firstOrNull { id ->
        val chars = manager.getCameraCharacteristics(id)
        chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    } ?: return CameraFov.DEFAULT
    val chars = manager.getCameraCharacteristics(backCameraId)
    val focals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
    val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    if (focals == null || focals.isEmpty() || sensorSize == null ||
        sensorSize.width <= 0 || sensorSize.height <= 0
    ) {
        return CameraFov.DEFAULT
    }

    val focal = focals.first()
    val fovH = 2.0 * Math.toDegrees(atan((sensorSize.width / 2.0) / focal))
    val fovV = 2.0 * Math.toDegrees(atan((sensorSize.height / 2.0) / focal))
    return CameraFov(
        horizontalDeg = fovH,
        verticalDeg = fovV,
        focalLengthMm = focal,
        sensorWidthMm = sensorSize.width,
        sensorHeightMm = sensorSize.height,
    )
}
