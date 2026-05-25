package studio.freestyle.labs.danjiangsunseeker.presentation.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import studio.freestyle.labs.danjiangsunseeker.R

// ── Data model ────────────────────────────────────────────────────────────────

private enum class LicenseType { BSD2, APACHE2 }

private data class LibraryInfo(
    val name: String,
    val version: String,
    val licenseType: LicenseType,
    /** 有此欄位表示使用方必須保留著作權聲明（BSD 2-Clause 等）。 */
    val attributionRequired: Boolean = false,
    val url: String,
)

/**
 * 所有專案直接使用且建議（或必須）聲明的第三方函式庫。
 *
 * BSD 2-Clause：必須在發行物中保留著作權聲明及授權條款。
 * Apache 2.0   ：無強制個別聲明義務，但建議列出以示尊重。
 */
private val libraries: List<LibraryInfo> = listOf(
    // ── BSD 2-Clause（必須聲明）────────────────────────────────────────────
    LibraryInfo(
        name = "MapLibre Android SDK",
        version = "11.5.2",
        licenseType = LicenseType.BSD2,
        attributionRequired = true,
        url = "https://github.com/maplibre/maplibre-gl-native",
    ),
    LibraryInfo(
        name = "MapLibre Annotation Plugin",
        version = "3.0.1",
        licenseType = LicenseType.BSD2,
        attributionRequired = true,
        url = "https://github.com/maplibre/maplibre-plugins-android",
    ),
    // ── Apache 2.0──────────────────────────────────────────────────────────
    LibraryInfo(
        name = "commons-suncalc",
        version = "3.11",
        licenseType = LicenseType.APACHE2,
        url = "https://github.com/shred/commons-suncalc",
    ),
    LibraryInfo(
        name = "ARCore",
        version = "1.45.0",
        licenseType = LicenseType.APACHE2,
        url = "https://github.com/google-ar/arcore-android-sdk",
    ),
    LibraryInfo(
        name = "Kotlin",
        version = "2.1.0",
        licenseType = LicenseType.APACHE2,
        url = "https://github.com/JetBrains/kotlin",
    ),
    LibraryInfo(
        name = "Jetpack Compose",
        version = "BOM 2024.12.01",
        licenseType = LicenseType.APACHE2,
        url = "https://developer.android.com/jetpack/compose",
    ),
    LibraryInfo(
        name = "Hilt / Dagger",
        version = "2.56.2",
        licenseType = LicenseType.APACHE2,
        url = "https://github.com/google/dagger",
    ),
    LibraryInfo(
        name = "Room",
        version = "2.6.1",
        licenseType = LicenseType.APACHE2,
        url = "https://developer.android.com/jetpack/androidx/releases/room",
    ),
    LibraryInfo(
        name = "Coil",
        version = "2.7.0",
        licenseType = LicenseType.APACHE2,
        url = "https://github.com/coil-kt/coil",
    ),
    LibraryInfo(
        name = "OkHttp",
        version = "4.12.0",
        licenseType = LicenseType.APACHE2,
        url = "https://github.com/square/okhttp",
    ),
    LibraryInfo(
        name = "Retrofit",
        version = "2.11.0",
        licenseType = LicenseType.APACHE2,
        url = "https://github.com/square/retrofit",
    ),
    LibraryInfo(
        name = "CameraX",
        version = "1.4.1",
        licenseType = LicenseType.APACHE2,
        url = "https://developer.android.com/jetpack/androidx/releases/camera",
    ),
    LibraryInfo(
        name = "Google Play Services Location",
        version = "21.3.0",
        licenseType = LicenseType.APACHE2,
        url = "https://developers.google.com/android/guides/setup",
    ),
    LibraryInfo(
        name = "kotlinx.serialization",
        version = "1.7.3",
        licenseType = LicenseType.APACHE2,
        url = "https://github.com/Kotlin/kotlinx.serialization",
    ),
    LibraryInfo(
        name = "kotlinx.coroutines",
        version = "1.9.0",
        licenseType = LicenseType.APACHE2,
        url = "https://github.com/Kotlin/kotlinx.coroutines",
    ),
    LibraryInfo(
        name = "DataStore",
        version = "1.1.1",
        licenseType = LicenseType.APACHE2,
        url = "https://developer.android.com/jetpack/androidx/releases/datastore",
    ),
    LibraryInfo(
        name = "WorkManager",
        version = "2.10.0",
        licenseType = LicenseType.APACHE2,
        url = "https://developer.android.com/jetpack/androidx/releases/work",
    ),
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun LicenseDetailScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.license_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.license_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
            }

            items(libraries) { lib ->
                LibraryCard(lib)
            }
        }
    }
}

// ── Library card ──────────────────────────────────────────────────────────────

@Composable
private fun LibraryCard(lib: LibraryInfo) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Name + open-in-browser icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lib.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.OpenInBrowser,
                    contentDescription = lib.url,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(lib.url))
                            )
                        }
                        .padding(4.dp),
                )
            }

            // Version
            Text(
                text = stringResource(R.string.license_version_label, lib.version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // License badge + attribution note
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (labelRes, chipColors) = when (lib.licenseType) {
                    LicenseType.BSD2 -> Pair(
                        R.string.license_badge_bsd2,
                        SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    )
                    LicenseType.APACHE2 -> Pair(
                        R.string.license_badge_apache2,
                        SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) },
                    colors = chipColors,
                )
                if (lib.attributionRequired) {
                    Text(
                        text = stringResource(R.string.license_note_attribution_required),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
