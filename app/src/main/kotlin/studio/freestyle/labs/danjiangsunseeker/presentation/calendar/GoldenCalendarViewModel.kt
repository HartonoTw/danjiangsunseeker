package studio.freestyle.labs.danjiangsunseeker.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import studio.freestyle.labs.danjiangsunseeker.data.hotspot.CustomHotspotStore
import studio.freestyle.labs.danjiangsunseeker.domain.model.DefaultHotspots
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.GoldenDate
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.ScanGoldenCalendarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class GoldenCalendarViewModel @Inject constructor(
    private val scanCalendar: ScanGoldenCalendarUseCase,
    private val customHotspotStore: CustomHotspotStore,
) : ViewModel() {

    private val _state = MutableStateFlow(GoldenCalendarState())
    val state: StateFlow<GoldenCalendarState> = _state.asStateFlow()

    private var customHotspots: List<Hotspot> = emptyList()

    init {
        customHotspotStore.hotspots
            .onEach { customs ->
                customHotspots = customs
                scan(_state.value.toleranceDegrees)
            }
            .launchIn(viewModelScope)
    }

    fun setTolerance(toleranceDeg: Double) {
        scan(toleranceDeg)
    }

    private fun mergedHotspots(): List<Hotspot> {
        val customsById = customHotspots.associateBy { it.id }
        val defaults = DefaultHotspots.ALL.map { customsById[it.id] ?: it }
        val pureCustoms = customHotspots.filter { c -> DefaultHotspots.ALL.none { it.id == c.id } }
        return defaults + pureCustoms
    }

    private fun scan(toleranceDeg: Double) {
        _state.value = _state.value.copy(loading = true, toleranceDegrees = toleranceDeg)
        viewModelScope.launch {
            val today = LocalDate.now(ZoneId.of("Asia/Taipei"))
            val hotspots = mergedHotspots()
            val result = withContext(Dispatchers.Default) {
                scanCalendar(
                    fromDate = today,
                    days = 365,
                    maxOffsetDegrees = toleranceDeg,
                    hotspots = hotspots,
                )
            }
            _state.value = _state.value.copy(
                loading = false,
                dates = result,
                toleranceDegrees = toleranceDeg,
            )
        }
    }
}

data class GoldenCalendarState(
    val loading: Boolean = true,
    val toleranceDegrees: Double = 2.0,
    val dates: List<GoldenDate> = emptyList(),
)
