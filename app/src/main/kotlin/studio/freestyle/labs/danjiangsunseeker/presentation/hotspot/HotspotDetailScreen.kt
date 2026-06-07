package studio.freestyle.labs.danjiangsunseeker.presentation.hotspot

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.model.TideKind
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.SunsetScore
import studio.freestyle.labs.danjiangsunseeker.presentation.common.MoonPhaseIcon
import studio.freestyle.labs.danjiangsunseeker.presentation.common.lunarPhaseLabel
import studio.freestyle.labs.danjiangsunseeker.presentation.common.verdictLabel
import java.time.format.DateTimeFormatter

@Composable
fun HotspotDetailScreen(
    hotspotId: String,
    onBack: () -> Unit,
    vm: HotspotListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val scored = state.predictions.firstOrNull { it.prediction.hotspot.id == hotspotId }
    val name = scored?.prediction?.hotspot?.let {
        it.nameRes?.let { res -> stringResource(res) } ?: it.customName.orEmpty()
    } ?: hotspotId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { pad ->
        if (state.loading || scored == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val prediction = scored.prediction
        val scrollState = rememberScrollState()
        var viewportHeightPx by remember { mutableIntStateOf(0) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .onSizeChanged { viewportHeightPx = it.height },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val description = prediction.hotspot.descriptionRes?.let { stringResource(it) }
                    ?: prediction.hotspot.description
                Text(description, style = MaterialTheme.typography.bodyLarge)
                val accessNote = prediction.hotspot.accessNoteRes?.let { stringResource(it) }
                    ?: prediction.hotspot.accessNote.takeIf { it.isNotEmpty() }
                if (accessNote != null) {
                    Text(
                        accessNote,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ScoreSection(scored.score)

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                DetailSection(stringResource(R.string.detail_sunset_events)) {
                    DetailRow(stringResource(R.string.label_sunset_time), prediction.events.sunset?.format(F))
                    DetailRow(stringResource(R.string.detail_sunrise), prediction.events.sunrise?.format(F))
                    DetailRow(stringResource(R.string.detail_solar_noon), prediction.events.solarNoon?.format(F))
                    DetailRow(stringResource(R.string.detail_golden_hour), "${prediction.events.goldenHourEveningStart?.format(F).orEmpty()} → ${prediction.events.goldenHourEveningEnd?.format(F).orEmpty()}")
                    DetailRow(stringResource(R.string.detail_blue_hour), "${prediction.events.blueHourEveningStart?.format(F).orEmpty()} → ${prediction.events.blueHourEveningEnd?.format(F).orEmpty()}")
                }

                DetailSection(stringResource(R.string.detail_relation_to_tower)) {
                    DetailRow(
                        stringResource(R.string.label_sun_azimuth),
                        prediction.events.sunsetAzimuthDegrees?.let { "%.2f".format(it) + "°" },
                    )
                    DetailRow(stringResource(R.string.label_tower_bearing), "%.2f".format(prediction.bearingToTowerDegrees) + "°")
                    DetailRow(
                        stringResource(R.string.label_alignment_offset),
                        prediction.alignmentOffsetDegrees?.let { "%+.3f".format(it) + "°" },
                    )
                    DetailRow(stringResource(R.string.label_distance_to_tower), "%.2f km".format(prediction.distanceToTowerMeters / 1000.0))
                    DetailRow(stringResource(R.string.detail_tower_angular_width), "%.4f".format(prediction.towerAngularWidthDegrees) + "°")
                }

                // ── 月相・潮汐 (付費功能) ──────────────────────────────
                val moon = prediction.moonInfo
                if (state.premiumUnlocked && moon != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    DetailSection(stringResource(R.string.moon_section)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            MoonPhaseIcon(
                                fractionLit = moon.fractionLit.toFloat(),
                                waxing = moon.waxing,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(lunarPhaseLabel(moon.phase), style = MaterialTheme.typography.bodyLarge)
                        }
                        DetailRow(
                            stringResource(R.string.moon_illumination),
                            stringResource(R.string.moon_illumination_value, "%.0f".format(moon.fractionLit * 100)),
                        )
                        DetailRow(stringResource(R.string.moon_rise), moon.rise?.format(HM))
                        DetailRow(stringResource(R.string.moon_set), moon.set?.format(HM))
                        DetailRow(
                            stringResource(R.string.moon_azimuth_at_set),
                            moon.azimuthAtSet?.let { "%.1f".format(it) + "°" },
                        )
                    }
                }

                val tide = prediction.tideInfo
                if (state.premiumUnlocked && tide != null) {
                    val meterUnit = stringResource(R.string.unit_meter)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    DetailSection(stringResource(R.string.tide_section)) {
                        if (tide.extremes.isEmpty()) {
                            Text(
                                stringResource(R.string.tide_none),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        tide.extremes.forEach { ex ->
                            val kindLabel = stringResource(
                                if (ex.kind == TideKind.HIGH) R.string.tide_high else R.string.tide_low,
                            )
                            DetailRow(
                                kindLabel,
                                "%s · %.2f %s".format(ex.time.format(HM), ex.heightMeters, meterUnit),
                            )
                        }
                    }
                }
            }

            DetailScrollBar(
                scrollValue = scrollState.value,
                maxScrollValue = scrollState.maxValue,
                viewportHeightPx = viewportHeightPx,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .width(4.dp)
                    .height(160.dp),
            )
        }
    }
}

@Composable
private fun ScoreSection(score: SunsetScore) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.detail_quality),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.detail_score_value, score.overall.toInt(), verdictLabel(score.verdict)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        ScoreBar(stringResource(R.string.detail_alignment), score.alignment)
    }
}

@Composable
private fun ScoreBar(label: String, value: Double) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            Text("${value.toInt()}", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { (value / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailScrollBar(
    scrollValue: Int,
    maxScrollValue: Int,
    viewportHeightPx: Int,
    modifier: Modifier = Modifier,
) {
    if (maxScrollValue <= 0 || viewportHeightPx <= 0) return

    val totalContentHeightPx = viewportHeightPx + maxScrollValue
    val thumbHeightFraction = (viewportHeightPx.toFloat() / totalContentHeightPx)
        .coerceIn(0.12f, 1f)
    val thumbTopFraction = (scrollValue.toFloat() / maxScrollValue)
        .coerceIn(0f, 1f) * (1f - thumbHeightFraction)
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

    Canvas(modifier = modifier) {
        val x = size.width / 2f
        drawLine(
            color = trackColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = size.width,
            cap = StrokeCap.Round,
        )
        val thumbTop = size.height * thumbTopFraction
        val thumbBottom = thumbTop + size.height * thumbHeightFraction
        drawLine(
            color = thumbColor,
            start = Offset(x, thumbTop),
            end = Offset(x, thumbBottom),
            strokeWidth = size.width,
            cap = StrokeCap.Round,
        )
    }
}

private val F: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
