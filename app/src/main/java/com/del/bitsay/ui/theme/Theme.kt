package com.del.bitsay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Which colour scheme the app uses. Stored per install; [SYSTEM] is the default. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Error = Color(0xFFD9736B)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3D3A38),
    onPrimary = Color(0xFFFFFDF7),
    primaryContainer = Color(0xFFFFD34E),
    onPrimaryContainer = Color(0xFF3D3A38),
    secondary = Color(0xFF8A8279),
    onSecondary = Color(0xFFFFFDF7),
    secondaryContainer = Color(0xFFB7E4C7),
    onSecondaryContainer = Color(0xFF3D3A38),
    tertiaryContainer = Color(0xFFAEDCEB),
    onTertiaryContainer = Color(0xFF3D3A38),
    background = Color(0xFFFCF3E8),
    onBackground = Color(0xFF3D3A38),
    surface = Color(0xFFFFFDF7),
    onSurface = Color(0xFF3D3A38),
    surfaceVariant = Color(0xFFEDE9E1),
    onSurfaceVariant = Color(0xFF8A8279),
    outline = Color(0xFFE6DDCB),
    error = Error,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF2EDE4),
    onPrimary = Color(0xFF211F1D),
    primaryContainer = Color(0xFF6B5320),
    onPrimaryContainer = Color(0xFFF2EDE4),
    secondary = Color(0xFFA79F94),
    onSecondary = Color(0xFF211F1D),
    secondaryContainer = Color(0xFF2F4F3C),
    onSecondaryContainer = Color(0xFFF2EDE4),
    tertiaryContainer = Color(0xFF29454F),
    onTertiaryContainer = Color(0xFFF2EDE4),
    background = Color(0xFF211F1D),
    onBackground = Color(0xFFF2EDE4),
    surface = Color(0xFF2C2A27),
    onSurface = Color(0xFFF2EDE4),
    surfaceVariant = Color(0xFF3A3733),
    onSurfaceVariant = Color(0xFFA79F94),
    outline = Color(0xFF443F39),
    error = Error,
)

/** Slightly irregular corners: the cheapest way to read as "hand drawn" rather than "corporate". */
val CuteShape = RoundedCornerShape(
    topStart = 22.dp,
    topEnd = 18.dp,
    bottomEnd = 24.dp,
    bottomStart = 18.dp,
)

val CuteShapeSmall = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 13.dp,
    bottomEnd = 17.dp,
    bottomStart = 13.dp,
)

private val AppShapes = Shapes(
    extraSmall = CuteShapeSmall,
    small = CuteShapeSmall,
    medium = CuteShape,
    large = CuteShape,
    extraLarge = CuteShape,
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Resolves [mode] against the phone's own setting. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun BitSayTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark()
    CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
