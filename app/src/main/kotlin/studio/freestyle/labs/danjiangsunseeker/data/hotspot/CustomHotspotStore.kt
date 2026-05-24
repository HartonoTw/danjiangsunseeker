package studio.freestyle.labs.danjiangsunseeker.data.hotspot

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
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
 * 使用者自訂熱點的儲存。所有 id 以 `custom_` 為前綴避免與內建熱點衝突。
 */
@Singleton
class CustomHotspotStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val ds = context.customHotspotDataStore
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    val hotspots: Flow<List<Hotspot>> = ds.data.map { prefs ->
        prefs[KEY_HOTSPOTS]?.let { decode(it) } ?: emptyList()
    }

    suspend fun all(): List<Hotspot> = hotspots.first()

    suspend fun upsert(hotspot: Hotspot) {
        require(hotspot.isCustom) { "Only custom hotspots can be upserted" }
        ds.edit { prefs ->
            val current = prefs[KEY_HOTSPOTS]?.let { decode(it) } ?: emptyList()
            val updated = if (current.any { it.id == hotspot.id }) {
                current.map { if (it.id == hotspot.id) hotspot else it }
            } else {
                current + hotspot
            }
            prefs[KEY_HOTSPOTS] = encode(updated)
        }
    }

    suspend fun remove(id: String) {
        ds.edit { prefs ->
            val current = prefs[KEY_HOTSPOTS]?.let { decode(it) } ?: emptyList()
            prefs[KEY_HOTSPOTS] = encode(current.filter { it.id != id })
        }
    }

    suspend fun replaceAll(hotspots: List<Hotspot>) {
        val customOnly = hotspots.filter { it.isCustom }
        ds.edit { it[KEY_HOTSPOTS] = encode(customOnly) }
    }

    /** 把目前所有自訂熱點轉成可匯出的 JSON 字串 (給匯出檔用)。 */
    suspend fun exportJson(): String = exportJsonOf(all())

    /** 匯出指定熱點清單為 JSON (給「全部熱點匯出」場景，包含預設 + 覆寫 + 純自訂)。 */
    fun exportJsonOf(hotspots: List<Hotspot>): String {
        val list = hotspots.map { CustomHotspotDto.from(it) }
        return json.encodeToString(ExportBundle(version = EXPORT_VERSION, hotspots = list))
    }

    /** 解析匯入的 JSON，回傳合法的 Hotspot 列表（不直接寫入）。 */
    fun parseImportJson(jsonText: String): List<Hotspot> {
        val bundle = json.decodeFromString<ExportBundle>(jsonText)
        return bundle.hotspots.map { it.toHotspot() }
    }

    private fun encode(list: List<Hotspot>): String =
        json.encodeToString(list.map { CustomHotspotDto.from(it) })

    private fun decode(s: String): List<Hotspot> =
        runCatching { json.decodeFromString<List<CustomHotspotDto>>(s).map { it.toHotspot() } }
            .getOrElse { emptyList() }

    companion object {
        private val KEY_HOTSPOTS = stringPreferencesKey("custom_hotspots")
        const val ID_PREFIX = "custom_"
        const val EXPORT_VERSION = 1
    }
}

@Serializable
private data class CustomHotspotDto(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val elev: Double = 0.0,
    val description: String = "",
    val accessNote: String = "",
) {
    fun toHotspot() = Hotspot(
        // 保留原 id：如果與預設熱點同 id (如 "sand_dune") → 視為覆寫；
        // 自訂建立的 id 都已含 "custom_" 前綴
        id = id,
        nameRes = null,
        customName = name,
        position = GeoPoint(lat, lon, elev),
        description = description,
        accessNote = accessNote,
    )
    companion object {
        fun from(h: Hotspot) = CustomHotspotDto(
            id = h.id,
            name = h.customName.orEmpty(),
            lat = h.position.latitude,
            lon = h.position.longitude,
            elev = h.position.elevationMeters,
            description = h.description,
            accessNote = h.accessNote,
        )
    }
}

@Serializable
private data class ExportBundle(
    val version: Int,
    val hotspots: List<CustomHotspotDto>,
)

private val Context.customHotspotDataStore by preferencesDataStore("custom_hotspots")
