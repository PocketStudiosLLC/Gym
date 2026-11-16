package com.gymapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gymapp.data.ThemeColor
import com.gymapp.data.ThemeMode

/** Tonal values for one preset: tone 40/90 for light mode, 80/30 for dark mode. */
private data class Tones(
    val swatch: Color,
    val light: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
    val dark: Color,
    val darkContainer: Color,
    val onDarkContainer: Color,
)

private val presets = mapOf(
    ThemeColor.BLUE to Tones(Color(0xFF1E88E5), Color(0xFF0061A4), Color(0xFFD1E4FF), Color(0xFF001D36), Color(0xFF9ECAFF), Color(0xFF00497D), Color(0xFFD1E4FF)),
    ThemeColor.TEAL to Tones(Color(0xFF00897B), Color(0xFF006A60), Color(0xFF74F8E5), Color(0xFF00201C), Color(0xFF53DBC9), Color(0xFF005048), Color(0xFF74F8E5)),
    ThemeColor.GREEN to Tones(Color(0xFF43A047), Color(0xFF006E1C), Color(0xFF94F990), Color(0xFF002204), Color(0xFF78DC77), Color(0xFF005313), Color(0xFF94F990)),
    ThemeColor.ORANGE to Tones(Color(0xFFF4511E), Color(0xFFA23F00), Color(0xFFFFDBCC), Color(0xFF351000), Color(0xFFFFB693), Color(0xFF7C2E00), Color(0xFFFFDBCC)),
    ThemeColor.RED to Tones(Color(0xFFE53935), Color(0xFFBB1614), Color(0xFFFFDAD5), Color(0xFF410001), Color(0xFFFFB4A9), Color(0xFF930006), Color(0xFFFFDAD5)),
    ThemeColor.PINK to Tones(Color(0xFFD81B60), Color(0xFFB4005C), Color(0xFFFFD9E2), Color(0xFF3E001B), Color(0xFFFFB1C8), Color(0xFF8A0045), Color(0xFFFFD9E2)),
    ThemeColor.PURPLE to Tones(Color(0xFF8E24AA), Color(0xFF8B25A6), Color(0xFFFFD6FE), Color(0xFF35003F), Color(0xFFF5AEFF), Color(0xFF6F008A), Color(0xFFFFD6FE)),
    ThemeColor.AMBER to Tones(Color(0xFFFFB300), Color(0xFF7C5800), Color(0xFFFFDEA6), Color(0xFF271900), Color(0xFFFBBC2E), Color(0xFF5E4200), Color(0xFFFFDEA6)),
)

fun swatchColor(color: ThemeColor): Color = presets.getValue(color).swatch

private fun schemeFor(mode: ThemeMode, color: ThemeColor): ColorScheme {
    val t = presets.getValue(color)
    return when (mode) {
        ThemeMode.DARK -> darkColorScheme(
            primary = t.dark,
            onPrimary = t.onLightContainer,
            primaryContainer = t.darkContainer,
            onPrimaryContainer = t.onDarkContainer,
            secondary = t.dark,
            secondaryContainer = t.darkContainer,
            onSecondaryContainer = t.onDarkContainer,
            tertiary = t.dark,
            background = Color(0xFF121212),
            onBackground = Color(0xFFE6E1E5),
            surface = Color(0xFF121212),
            onSurface = Color(0xFFE6E1E5),
            surfaceVariant = Color(0xFF2A2A2E),
            onSurfaceVariant = Color(0xFFC7C5CE),
            surfaceContainerLowest = Color(0xFF0D0D0D),
            surfaceContainerLow = Color(0xFF1A1A1C),
            surfaceContainer = Color(0xFF1E1E21),
            surfaceContainerHigh = Color(0xFF262629),
            surfaceContainerHighest = Color(0xFF303034),
            outline = Color(0xFF8F909A),
            outlineVariant = Color(0xFF45464F),
        )
        ThemeMode.LIGHT -> lightColorScheme(
            primary = t.light,
            onPrimary = Color.White,
            primaryContainer = t.lightContainer,
            onPrimaryContainer = t.onLightContainer,
            secondary = t.light,
            secondaryContainer = t.lightContainer,
            onSecondaryContainer = t.onLightContainer,
            tertiary = t.light,
            background = Color(0xFFFCFCFF),
            surface = Color(0xFFFCFCFF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF5F5F8),
            surfaceContainer = Color(0xFFEFEFF2),
            surfaceContainerHigh = Color(0xFFE9E9EC),
            surfaceContainerHighest = Color(0xFFE3E3E6),
        )
    }
}

/** Superset group colors: distinct from each other and readable in both modes. */
private val supersetDark = listOf(
    Color(0xFFFF8A65), Color(0xFF4DD0E1), Color(0xFFAED581),
    Color(0xFFBA68C8), Color(0xFFFFD54F), Color(0xFFF06292),
)
private val supersetLight = listOf(
    Color(0xFFD84315), Color(0xFF00838F), Color(0xFF558B2F),
    Color(0xFF7B1FA2), Color(0xFFB28704), Color(0xFFC2185B),
)

@Composable
fun supersetColor(index: Int): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val list = if (dark) supersetDark else supersetLight
    return list[((index % list.size) + list.size) % list.size]
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
fun GymTheme(mode: ThemeMode, color: ThemeColor, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schemeFor(mode, color), content = content)
}
