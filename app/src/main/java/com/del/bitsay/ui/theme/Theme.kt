package com.del.bitsay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Error = Color(0xFFD9736B)

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Sun,
    onPrimaryContainer = Ink,
    secondary = InkSoft,
    onSecondary = Paper,
    secondaryContainer = Mint,
    onSecondaryContainer = Ink,
    tertiaryContainer = Sky,
    onTertiaryContainer = Ink,
    background = Bg,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Done,
    onSurfaceVariant = InkSoft,
    outline = Line,
    error = Error,
)

private val DarkScheme = darkColorScheme(
    primary = InkDark,
    onPrimary = BgDark,
    primaryContainer = Sun,
    onPrimaryContainer = Ink,
    secondary = InkSoftDark,
    onSecondary = PaperDark,
    secondaryContainer = Mint,
    onSecondaryContainer = Ink,
    tertiaryContainer = Sky,
    onTertiaryContainer = Ink,
    background = BgDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = LineDark,
    onSurfaceVariant = InkSoftDark,
    outline = LineDark,
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

@Composable
fun BitSayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
