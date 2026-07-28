package net.bdfz.recite.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Paper = Color(0xFFF7F2E8)
private val PaperDark = Color(0xFF1A1D1A)
private val Ink = Color(0xFF171A17)
private val Vermilion = Color(0xFFA63A2B)
private val Jade = Color(0xFF507267)
private val Mist = Color(0xFFE8E1D5)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Mist,
    onPrimaryContainer = Ink,
    secondary = Jade,
    onSecondary = Color.White,
    tertiary = Vermilion,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFBF3),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE7DC),
    onSurfaceVariant = Color(0xFF4B4D48),
    outline = Color(0xFF7D7C74),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFECE5D8),
    onPrimary = Ink,
    primaryContainer = Color(0xFF343832),
    onPrimaryContainer = Color(0xFFF7F2E8),
    secondary = Color(0xFF9DBFAF),
    tertiary = Color(0xFFFFB4A7),
    background = PaperDark,
    onBackground = Color(0xFFF0EAE0),
    surface = Color(0xFF222622),
    onSurface = Color(0xFFF0EAE0),
    surfaceVariant = Color(0xFF373B36),
    onSurfaceVariant = Color(0xFFD0CEC7),
)

@Composable
fun LangLangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme && Build.VERSION.SDK_INT >= 26
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
