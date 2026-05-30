package studio.freestyle.labs.danjiangsunseeker.presentation.about

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import studio.freestyle.labs.danjiangsunseeker.BuildConfig
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.data.settings.AppLanguage
import studio.freestyle.labs.danjiangsunseeker.data.settings.LocaleManager

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onShowLicenses: () -> Unit,
    onShowChangelog: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── App icon（同 launcher：橘底 + 主塔/夕陽前景）──────────────
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(colorResource(R.color.sunset_orange)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(112.dp),
                )
            }

            // ── App name ──────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            // ── Version chip ──────────────────────────────────────────────
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        text = stringResource(R.string.about_version_label, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            // ── Language selector ─────────────────────────────────────────
            LanguageSelector()

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            // ── Description ───────────────────────────────────────────────
            Text(
                text = stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            // ── Changelog ─────────────────────────────────────────────────
            FilledTonalButton(
                onClick = onShowChangelog,
            ) {
                Text(stringResource(R.string.about_changelog))
            }

            // ── Open-source licenses ──────────────────────────────────────
            FilledTonalButton(
                onClick = onShowLicenses,
            ) {
                Text(stringResource(R.string.about_open_source_licenses))
            }

            Spacer(Modifier.height(32.dp))

            // ── Copyright ─────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.about_copyright),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LanguageSelector() {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(LocaleManager.getLanguage(ctx)) }
    val options = listOf(
        AppLanguage.SYSTEM to R.string.language_system,
        AppLanguage.CHINESE to R.string.language_chinese,
        AppLanguage.ENGLISH to R.string.language_english,
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (lang, labelRes) ->
                FilterChip(
                    selected = current == lang,
                    onClick = {
                        if (current != lang) {
                            current = lang
                            LocaleManager.setLanguage(ctx, lang)
                            // 重建 Activity 讓新語系立即套用
                            ctx.findActivity()?.recreate()
                        }
                    },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
