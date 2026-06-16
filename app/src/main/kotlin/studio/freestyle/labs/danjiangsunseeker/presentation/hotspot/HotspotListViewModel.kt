package studio.freestyle.labs.danjiangsunseeker.presentation.hotspot

import android.content.Context
import studio.freestyle.labs.danjiangsunseeker.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.freestyle.labs.danjiangsunseeker.data.hotspot.CustomHotspotStore
import studio.freestyle.labs.danjiangsunseeker.data.settings.TowerTargetStore
import studio.freestyle.labs.danjiangsunseeker.data.sensors.LocationProvider
import studio.freestyle.labs.danjiangsunseeker.domain.model.DefaultHotspots
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.ComputeSunsetScoreUseCase
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.HotspotPrediction
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.PredictHotspotsUseCase
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.SunsetScore
import studio.freestyle.labs.danjiangsunseeker.domain.premium.PremiumGate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import javax.inject.Inject

// WeatherForecast / WeatherRepository 已移除（CWA API 停用）

@HiltViewModel
class HotspotListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val predictHotspots: PredictHotspotsUseCase,
    private val computeScore: ComputeSunsetScoreUseCase,
    private val customHotspotStore: CustomHotspotStore,
    private val locationProvider: LocationProvider,
    private val towerTargetStore: TowerTargetStore,
    private val premiumGate: PremiumGate,
) : ViewModel() {

    private val _state = MutableStateFlow(HotspotListState())
    val state: StateFlow<HotspotListState> = _state.asStateFlow()

    private var customHotspots: List<Hotspot> = emptyList()

    /** 最新 GPS 位置（背景取一次即可，供新增熱點預填） */
    private var lastKnownGps: GeoPoint? = null

    init {
        viewModelScope.launch {
            runCatching {
                val loc = locationProvider.locationUpdates()
                    .catch { /* 無權限靜默略過 */ }
                    .first()
                lastKnownGps = GeoPoint(loc.latitude, loc.longitude, loc.altitude)
            }
        }
    }

    init {
        customHotspotStore.hotspots
            .onEach { list ->
                customHotspots = list
                loadFor(_state.value.date)
            }
            .launchIn(viewModelScope)
        towerTargetStore.target
            .onEach { target ->
                _state.value = _state.value.copy(towerTarget = target)
                loadFor(_state.value.date)
            }
            .launchIn(viewModelScope)
        premiumGate.isPremium(studio.freestyle.labs.danjiangsunseeker.domain.premium.PremiumPage.HOTSPOTS)
            .onEach { unlocked ->
                if (unlocked != _state.value.premiumUnlocked) {
                    // 鎖定時強制回到太陽模式，避免殘留在月亮模式
                    val body = if (unlocked) _state.value.body else HotspotBody.SUN
                    _state.value = _state.value.copy(premiumUnlocked = unlocked, body = body)
                    loadFor(_state.value.date)
                }
            }
            .launchIn(viewModelScope)
        loadFor(LocalDate.now(java.time.ZoneId.of("Asia/Taipei")))
    }

    /**
     * 合併熱點：custom store 的條目會覆寫同 id 的預設熱點；其餘新自訂熱點 (id 不在預設清單中) 直接附加。
     */
    private fun mergedHotspots(): List<Hotspot> {
        val customsById = customHotspots.associateBy { it.id }
        val defaultsList = DefaultHotspots.ALL.map { customsById[it.id] ?: it }
        val pureCustoms = customHotspots.filter { c -> DefaultHotspots.ALL.none { it.id == c.id } }
        return defaultsList + pureCustoms
    }

    fun loadFor(date: LocalDate) {
        _state.value = _state.value.copy(loading = true, date = date, error = null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val all = mergedHotspots()
                    val predictions = predictHotspots(
                        date, all, _state.value.towerTarget,
                        includeMoonTide = _state.value.premiumUnlocked,
                        useMoon = _state.value.body == HotspotBody.MOON,
                    )
                    val customIds = customHotspots.map { it.id }.toSet()
                    predictions.map { p ->
                        ScoredPrediction(
                            prediction = p,
                            score = computeScore(p.alignmentOffsetDegrees),
                            isOverride = p.hotspot.id in customIds,
                        )
                    }.sortedByDescending { it.score.overall }
                }
            }.onSuccess { predictions ->
                _state.value = _state.value.copy(
                    predictions = predictions,
                    loading = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message)
            }
        }
    }

    fun showEditor(existing: Hotspot? = null) {
        val resolvedName = existing?.let {
            it.nameRes?.let { res -> context.getString(res) } ?: it.customName.orEmpty()
        } ?: ""
        val isOverride = existing != null && customHotspots.any { it.id == existing.id }
        val isPureCustom = existing != null &&
            DefaultHotspots.ALL.none { it.id == existing.id } &&
            customHotspots.any { it.id == existing.id }

        // 新增熱點（existing == null）時，以 GPS 目前位置預填座標
        val gps = if (existing == null) lastKnownGps else null
        _state.value = _state.value.copy(
            editor = HotspotEditorState(
                editingId = existing?.id,
                name = resolvedName,
                latitude = existing?.position?.latitude?.let { "%.6f".format(it) }
                    ?: gps?.latitude?.let { "%.6f".format(it) }.orEmpty(),
                longitude = existing?.position?.longitude?.let { "%.6f".format(it) }
                    ?: gps?.longitude?.let { "%.6f".format(it) }.orEmpty(),
                elevation = (existing?.position?.elevationMeters ?: gps?.elevationMeters ?: 0.0).toString(),
                description = existing?.description.orEmpty(),
                canDelete = isOverride,
                canResetToDefault = isOverride && !isPureCustom,
                originalIsDefault = existing != null && DefaultHotspots.ALL.any { it.id == existing.id },
            ),
        )
    }

    fun closeEditor() {
        _state.value = _state.value.copy(editor = null)
    }

    fun openLocationPicker() {
        val ed = _state.value.editor ?: return
        _state.value = _state.value.copy(editor = ed.copy(showLocationPicker = true))
    }

    fun closeLocationPicker() {
        val ed = _state.value.editor ?: return
        _state.value = _state.value.copy(editor = ed.copy(showLocationPicker = false))
    }

    fun flyEditorToCurrentLocation() {
        val ed = _state.value.editor ?: return
        if (ed.locatingCurrentLocation) return
        if (!locationProvider.hasPermission()) {
            _state.value = _state.value.copy(
                editor = ed.copy(locationMessage = context.getString(R.string.msg_need_location_permission)),
            )
            return
        }

        _state.value = _state.value.copy(
            editor = ed.copy(locatingCurrentLocation = true, locationMessage = null),
        )
        viewModelScope.launch {
            runCatching {
                withTimeout(10_000L) {
                    withContext(Dispatchers.IO) {
                        locationProvider.locationUpdates(intervalMillis = 1_000L).first()
                    }
                }
            }.onSuccess { loc ->
                val current = _state.value.editor ?: return@onSuccess
                lastKnownGps = GeoPoint(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    elevationMeters = if (loc.hasAltitude()) loc.altitude else 0.0,
                )
                _state.value = _state.value.copy(
                    editor = current.copy(
                        latitude = "%.6f".format(loc.latitude),
                        longitude = "%.6f".format(loc.longitude),
                        elevation = (if (loc.hasAltitude()) loc.altitude else 0.0).toString(),
                        locatingCurrentLocation = false,
                        currentLocationFlyRequest = current.currentLocationFlyRequest + 1,
                    ),
                )
            }.onFailure { e ->
                val current = _state.value.editor ?: return@onFailure
                _state.value = _state.value.copy(
                    editor = current.copy(
                        locatingCurrentLocation = false,
                        locationMessage = context.getString(R.string.msg_location_unavailable),
                    ),
                )
            }
        }
    }

    fun clearEditorLocationMessage() {
        val ed = _state.value.editor ?: return
        _state.value = _state.value.copy(editor = ed.copy(locationMessage = null))
    }

    fun updateEditorField(field: EditorField, value: String) {
        val ed = _state.value.editor ?: return
        _state.value = _state.value.copy(
            editor = when (field) {
                EditorField.NAME -> ed.copy(name = value)
                EditorField.LAT -> ed.copy(latitude = value)
                EditorField.LON -> ed.copy(longitude = value)
                EditorField.ELEV -> ed.copy(elevation = value)
                EditorField.DESC -> ed.copy(description = value)
            },
        )
    }

    fun saveEditor() {
        val ed = _state.value.editor ?: return
        val lat = ed.latitude.toDoubleOrNull()
        val lon = ed.longitude.toDoubleOrNull()
        val elev = ed.elevation.toDoubleOrNull() ?: 0.0
        if (ed.name.isBlank() || lat == null || lon == null ||
            lat !in -90.0..90.0 || lon !in -180.0..180.0
        ) {
            _state.value = _state.value.copy(
                editor = ed.copy(error = context.getString(R.string.msg_invalid_name_coords)),
            )
            return
        }
        // 編輯既有：用既有 id (可能是預設 id 或 custom_ id)；新增則生新 id
        val id = ed.editingId ?: "${CustomHotspotStore.ID_PREFIX}${System.currentTimeMillis()}"
        val hotspot = Hotspot(
            id = id,
            nameRes = null,
            customName = ed.name.trim(),
            position = GeoPoint(lat, lon, elev),
            description = ed.description.trim(),
        )
        viewModelScope.launch {
            runCatching { customHotspotStore.upsert(hotspot) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        editor = null,
                        importMessage = if (ed.isEditing) context.getString(R.string.msg_updated) else context.getString(R.string.msg_added),
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        editor = ed.copy(error = context.getString(R.string.msg_save_failed, e.message ?: "")),
                    )
                }
        }
    }

    /** 刪除自訂條目；若為預設覆寫則回復預設值。 */
    fun deleteFromEditor() {
        val ed = _state.value.editor ?: return
        val id = ed.editingId ?: return
        viewModelScope.launch {
            customHotspotStore.remove(id)
            _state.value = _state.value.copy(
                editor = null,
                importMessage = if (ed.canResetToDefault) context.getString(R.string.msg_reset_to_default) else context.getString(R.string.msg_deleted),
            )
        }
    }

    suspend fun exportJson(): String {
        // 匯出全部熱點 (預設 + 覆寫 + 純自訂)，預設名稱解析成字串
        val flattened = mergedHotspots().map { hotspot ->
            val nameRes = hotspot.nameRes
            if (nameRes != null) {
                hotspot.copy(nameRes = null, customName = context.getString(nameRes))
            } else hotspot
        }
        return customHotspotStore.exportJsonOf(flattened)
    }

    fun importJson(jsonText: String) {
        viewModelScope.launch {
            runCatching {
                val incoming = customHotspotStore.parseImportJson(jsonText)
                val current = customHotspotStore.all().associateBy { it.id }.toMutableMap()
                incoming.forEach { current[it.id] = it }
                customHotspotStore.replaceAll(current.values.toList())
                _state.value = _state.value.copy(
                    importMessage = context.getString(R.string.msg_import_success, incoming.size),
                )
            }.onFailure {
                _state.value = _state.value.copy(importMessage = context.getString(R.string.msg_import_failed, it.message ?: ""))
            }
        }
    }

    fun clearImportMessage() {
        _state.value = _state.value.copy(importMessage = null)
    }

    fun setTowerTarget(target: TowerTarget) {
        viewModelScope.launch { towerTargetStore.setTarget(target) }
    }

    /** 切換預測天體（太陽 / 月亮）。月亮為付費功能，鎖定時忽略。 */
    fun setBody(body: HotspotBody) {
        if (body == HotspotBody.MOON && !_state.value.premiumUnlocked) return
        if (body == _state.value.body) return
        _state.value = _state.value.copy(body = body)
        loadFor(_state.value.date)
    }
}

/** 熱點列表的預測天體：夕陽穿塔 / 月亮穿塔。 */
enum class HotspotBody { SUN, MOON }

data class HotspotListState(
    val date: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Taipei")),
    val predictions: List<ScoredPrediction> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val editor: HotspotEditorState? = null,
    val importMessage: String? = null,
    val towerTarget: TowerTarget = TowerTarget.UpperY,
    /** 月相/潮汐付費功能是否解鎖 (決定是否計算與顯示)。 */
    val premiumUnlocked: Boolean = false,
    /** 預測天體：太陽 / 月亮（月亮為付費功能）。 */
    val body: HotspotBody = HotspotBody.SUN,
)

data class HotspotEditorState(
    val editingId: String? = null,
    val name: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val elevation: String = "0",
    val description: String = "",
    val error: String? = null,
    val canDelete: Boolean = false,
    val canResetToDefault: Boolean = false,
    val originalIsDefault: Boolean = false,
    /** true 時顯示地圖選點覆蓋層，取代編輯 Dialog */
    val showLocationPicker: Boolean = false,
    val locatingCurrentLocation: Boolean = false,
    val currentLocationFlyRequest: Int = 0,
    val locationMessage: String? = null,
) {
    val isEditing: Boolean get() = editingId != null
}

enum class EditorField { NAME, LAT, LON, ELEV, DESC }

data class ScoredPrediction(
    val prediction: HotspotPrediction,
    val score: SunsetScore,
    val isOverride: Boolean = false,
)
