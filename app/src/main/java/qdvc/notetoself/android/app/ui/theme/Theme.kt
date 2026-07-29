package qdvc.notetoself.android.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import qdvc.notetoself.android.app.model.ThemeMode
import qdvc.notetoself.android.app.model.ThemeSpec

private fun hex(s: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrDefault(Color.Unspecified)

fun ThemeSpec.toColorScheme(): ColorScheme {
    val c = colors
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val onBackground = hex(c["onBackground"])
    return base.copy(
        background = hex(c["background"]),
        surface = hex(c["surface"]),
        surfaceVariant = hex(c["surfaceVariant"]),
        onBackground = onBackground,
        onSurface = onBackground,
        onSurfaceVariant = hex(c["onSurfaceVariant"]),
        outline = hex(c["outline"]),
        primary = hex(c["primary"]),
        onPrimary = hex(c["onPrimary"]),
        secondary = hex(c["secondary"]),
        onSecondary = hex(c["onSecondary"]),
        error = hex(c["error"]),
    )
}

fun resolveDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.AUTOMATIC -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun QdvcNtsTheme(
    spec: ThemeSpec,
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val scheme = spec.toColorScheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val barColor = scheme.surface.toArgb()
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = barColor
            @Suppress("DEPRECATION")
            window.navigationBarColor = barColor
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
