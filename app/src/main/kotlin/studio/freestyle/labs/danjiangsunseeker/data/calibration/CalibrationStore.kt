package studio.freestyle.labs.danjiangsunseeker.data.calibration

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AR 太陽校正紀錄。每次成功校正會新增一筆，最多保留 [MAX_RECORDS] 筆。
 *
 * Why 不用 Room: 只需要一張表 5 筆資料，preferences DataStore 已足夠；省去 Room schema 與 DAO 樣板。
 */
@Singleton
class CalibrationStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val ds = context.calibrationDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val records: Flow<List<CalibrationRecord>> = ds.data.map { prefs ->
        prefs[KEY_RECORDS]?.let { runCatching { json.decodeFromString<List<CalibrationRecord>>(it) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun latest(): CalibrationRecord? = records.first().firstOrNull()

    suspend fun add(record: CalibrationRecord) {
        ds.edit { prefs ->
            val current = prefs[KEY_RECORDS]?.let {
                runCatching { json.decodeFromString<List<CalibrationRecord>>(it) }.getOrNull()
            } ?: emptyList()
            val updated = (listOf(record) + current).take(MAX_RECORDS)
            prefs[KEY_RECORDS] = json.encodeToString(updated)
        }
    }

    suspend fun clearAll() {
        ds.edit { it.remove(KEY_RECORDS) }
    }

    companion object {
        const val MAX_RECORDS = 5
        private val KEY_RECORDS = stringPreferencesKey("calibration_records")
    }
}

@Serializable
data class CalibrationRecord(
    val azimuthOffsetDeg: Double,
    val pitchOffsetDeg: Double,
    val timestampEpochMs: Long,
    val observerLat: Double,
    val observerLon: Double,
)

private val Context.calibrationDataStore by preferencesDataStore("ar_calibration")
