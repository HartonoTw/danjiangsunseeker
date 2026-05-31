package studio.freestyle.labs.danjiangsunseeker.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = SunsetOrange,
    onPrimary = CloudWhite,
    primaryContainer = Color(0xFFFFDFCB),
    onPrimaryContainer = Color(0xFF3A1608),
    secondary = DeepTeal,
    onSecondary = CloudWhite,
    secondaryContainer = Color(0xFFCDEFF0),
    onSecondaryContainer = Color(0xFF052A2E),
    tertiary = SunsetAmber,
    onTertiary = InkBlue,
    tertiaryContainer = Color(0xFFFFE4B4),
    onTertiaryContainer = Color(0xFF321C00),
    background = LightBackground,
    onBackground = InkBlue,
    surface = CloudWhite,
    onSurface = InkBlue,
    surfaceVariant = MistBlue,
    onSurfaceVariant = Slate,
    outline = Slate,
)

private val DarkColors = darkColorScheme(
    primary = SunsetAmber,
    onPrimary = DarkBackground,
    primaryContainer = Color(0xFF5E2A10),
    onPrimaryContainer = Color(0xFFFFD9C2),
    secondary = Color(0xFF8DD7DA),
    onSecondary = Color(0xFF06282C),
    tertiary = GoldHour,
    onTertiary = Color(0xFF2B1B00),
    background = DarkBackground,
    onBackground = Color(0xFFE8ECEE),
    surface = Color(0xFF17212A),
    onSurface = Color(0xFFE8ECEE),
    surfaceVariant = Color(0xFF23313B),
    onSurfaceVariant = Color(0xFFC0CAD0),
    outline = Color(0xFF93A0A8),
)

@Composable
fun DanjiangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
