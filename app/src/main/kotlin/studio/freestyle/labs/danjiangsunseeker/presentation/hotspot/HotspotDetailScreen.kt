package studio.freestyle.labs.danjiangsunseeker.presentation.hotspot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.SunsetScore
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
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(prediction.hotspot.description, style = MaterialTheme.typography.bodyLarge)
            if (prediction.hotspot.accessNote.isNotEmpty()) {
                Text(
                    prediction.hotspot.accessNote,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            ScoreSection(scored.score)

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            DetailSection("日落事件") {
                DetailRow(stringResource(R.string.label_sunset_time), prediction.events.sunset?.format(F))
                DetailRow("日出", prediction.events.sunrise?.format(F))
                DetailRow("正午", prediction.events.solarNoon?.format(F))
                DetailRow("黃金時刻", "${prediction.events.goldenHourEveningStart?.format(F).orEmpty()} → ${prediction.events.goldenHourEveningEnd?.format(F).orEmpty()}")
                DetailRow("藍調時刻", "${prediction.events.blueHourEveningStart?.format(F).orEmpty()} → ${prediction.events.blueHourEveningEnd?.format(F).orEmpty()}")
            }

            DetailSection("與主塔的關係") {
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
                DetailRow("主塔角寬 (可容忍誤差)", "%.4f".format(prediction.towerAngularWidthDegrees) + "°")
            }
        }
    }
}

@Composable
private fun ScoreSection(score: SunsetScore) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "拍攝品質",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${score.overall.toInt()} / 100  ${score.verdict}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        ScoreBar("對齊度", score.alignment)
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
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium)
    }
}

private val F: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
