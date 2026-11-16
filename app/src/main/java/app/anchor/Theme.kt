package app.anchor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1B6C73),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8EEF4),
    onPrimaryContainer = Color(0xFF002023),
    secondary = Color(0xFF4A6365),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8EA),
    onSecondaryContainer = Color(0xFF051F21),
    tertiary = Color(0xFF4C5F7C),
    onTertiary = Color.White,
    background = Color(0xFFF5FAFB),
    onBackground = Color(0xFF171D1E),
    surface = Color(0xFFF5FAFB),
    onSurface = Color(0xFF171D1E),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4849),
    outline = Color(0xFF6F797A),
    outlineVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF6F7),
    surfaceContainer = Color(0xFFEAF3F4),
    surfaceContainerHigh = Color(0xFFE5F0F1),
    surfaceContainerHighest = Color(0xFFE0ECED),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8CD2D8),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF004F55),
    onPrimaryContainer = Color(0xFFA8EEF4),
    secondary = Color(0xFFB1CBCE),
    onSecondary = Color(0xFF1C3437),
    secondaryContainer = Color(0xFF324B4D),
    onSecondaryContainer = Color(0xFFCCE8EA),
    tertiary = Color(0xFFB4C8E9),
    onTertiary = Color(0xFF1D314B),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDEE4E4),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDEE4E4),
    surfaceVariant = Color(0xFF3F4849),
    onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4849),
    surfaceContainerLowest = Color(0xFF090F10),
    surfaceContainerLow = Color(0xFF171D1E),
    surfaceContainer = Color(0xFF1B2122),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303637),
)

@Composable
fun AnchorTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
