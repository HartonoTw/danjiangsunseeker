package studio.freestyle.labs.danjiangsunseeker.data.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 手機朝向偵測器。封裝 SensorManager 的 TYPE_ROTATION_VECTOR + GeomagneticField，
 * 輸出 **真北方位角** (含磁偏角修正) + 俯仰角 + 翻滾角。
 *
 * 使用方式:
 *  - 在 onResume/onStart 呼叫 [start]
 *  - 在 onPause/onStop 呼叫 [stop]
 *  - 收到位置更新時呼叫 [updateLocation] 重算磁偏角 (台灣約 -4.5°)
 *
 * Why TYPE_ROTATION_VECTOR 而非 ACCELEROMETER + MAGNETIC_FIELD:
 *  - ROTATION_VECTOR 已由系統融合加速度計 + 磁力計 + 陀螺儀，比手動融合穩定許多
 *  - 不需處理低通濾波或飄移
 *  - 直接回傳四元數，轉旋轉矩陣即可用
 */
@Singleton
class DeviceOrientationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    private val _orientation = MutableStateFlow(DeviceOrientation())
    val orientation: StateFlow<DeviceOrientation> = _orientation.asStateFlow()

    /** 磁偏角 (度)。台灣約 -4.5°，由 [updateLocation] 動態更新。 */
    @Volatile private var declinationDegrees: Float = -4.5f

    /** 螢幕顯示旋轉角度 (Surface.ROTATION_0/90/180/270)；用於把手機座標映射到地理座標。 */
    @Volatile private var displayRotation: Int = Surface.ROTATION_0

    /** 是否有 ROTATION_VECTOR 感測器；極少數老機沒有，需 fallback。 */
    val isAvailable: Boolean get() = rotationVectorSensor != null

    fun start() {
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun setDisplayRotation(rotation: Int) {
        displayRotation = rotation
    }

    /** 當收到新的 GPS 位置時呼叫，重新計算當地磁偏角。 */
    fun updateLocation(latitude: Double, longitude: Double, altitudeMeters: Double) {
        val geoField = GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            altitudeMeters.toFloat(),
            System.currentTimeMillis(),
        )
        declinationDegrees = geoField.declination
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // 把手機座標系映射到地理座標系 (手機朝向地平線拍攝時，相機光軸 = Y 軸負方向)
        // 對於使用者「直立握手機，相機朝前拍攝景色」的場景，使用 AXIS_X / AXIS_Z 重映射
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Z
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
        SensorManager.getOrientation(remappedMatrix, orientationValues)

        // values[0] = azimuth (-π..π) 由磁北順時針
        // values[1] = pitch (-π..π) 手機向前傾斜為負，向後傾斜為正
        // values[2] = roll  (-π..π) 手機左傾為正
        val magneticAzDeg = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(orientationValues[2].toDouble()).toFloat()

        val trueAzDeg = normalize360(magneticAzDeg + declinationDegrees)

        _orientation.value = DeviceOrientation(
            trueAzimuthDegrees = trueAzDeg,
            pitchDegrees = -pitchDeg,  // 反轉：相機指向地平線時 pitch=0，仰望為正，俯瞰為負
            rollDegrees = rollDeg,
            declinationDegrees = declinationDegrees,
            accuracy = event.accuracy,
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不需特別處理；最新 accuracy 已寫入 _orientation
    }

    private fun normalize360(deg: Float): Float = ((deg % 360f) + 360f) % 360f
}

/**
 * 手機朝向。
 * @param trueAzimuthDegrees 相機光軸朝向 (0..360°，正北 0°，順時針)
 * @param pitchDegrees 仰角 (相機朝水平為 0°，朝天空為正，朝地面為負)
 * @param rollDegrees 翻滾角 (-180..180°，左傾正)
 * @param declinationDegrees 當地磁偏角 (度)，已套用到 trueAzimuth
 * @param accuracy SensorManager.SENSOR_STATUS_*
 */
data class DeviceOrientation(
    val trueAzimuthDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val declinationDegrees: Float = 0f,
    val accuracy: Int = 0,
)
