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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Which colour scheme the app uses. Stored per install; [SYSTEM] is the default. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Error = Color(0xFFD9736B)

// Sourced from design/css/tokens.css. `primary` is the accent, so anything that defaults to it
// (FAB container, checked controls) lands on the one loud element without being told to.
private val LightScheme = lightColorScheme(
    primary = LightPalette.accent,
    onPrimary = LightPalette.accentInk,
    primaryContainer = LightPalette.accentSoft,
    onPrimaryContainer = LightPalette.accent,
    secondary = LightPalette.inkSoft,
    onSecondary = LightPalette.paper,
    secondaryContainer = LightPalette.accentSoft,
    onSecondaryContainer = LightPalette.ink,
    background = LightPalette.background,
    onBackground = LightPalette.ink,
    surface = LightPalette.paper,
    onSurface = LightPalette.ink,
    surfaceVariant = LightPalette.accentSoft,
    onSurfaceVariant = LightPalette.inkSoft,
    outline = LightPalette.line,
    error = Error,
)

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.accent,
    onPrimary = DarkPalette.accentInk,
    primaryContainer = DarkPalette.accentSoft,
    onPrimaryContainer = DarkPalette.accent,
    secondary = DarkPalette.inkSoft,
    onSecondary = DarkPalette.background,
    secondaryContainer = DarkPalette.accentSoft,
    onSecondaryContainer = DarkPalette.ink,
    background = DarkPalette.background,
    onBackground = DarkPalette.ink,
    surface = DarkPalette.paper,
    onSurface = DarkPalette.ink,
    surfaceVariant = DarkPalette.accentSoft,
    onSurfaceVariant = DarkPalette.inkSoft,
    outline = DarkPalette.line,
    error = Error,
)

// ----------------------------------------------------------------------------
// Shape. Taken straight from tokens.css.
//
// The corners are asymmetric **in a regular way**: one diagonal pair is fuller than the other.
// Four identical corners read as industrial; random per-corner values read as misaligned. This is
// the middle: it has a direction, and it repeats, so it looks intended.
// ----------------------------------------------------------------------------

/** `--r-card` — list rows, the editor sheet, the widget. */
val CardShape = RoundedCornerShape(
    topStart = 26.dp,
    topEnd = 16.dp,
    bottomEnd = 26.dp,
    bottomStart = 16.dp,
)

/** `--r-sm` — widget rows, nested blocks. */
val SmallShape = RoundedCornerShape(
    topStart = 17.dp,
    topEnd = 11.dp,
    bottomEnd = 17.dp,
    bottomStart = 11.dp,
)

/** `--r-block` — settings groups, dialogs, the segmented-tab trough. */
val BlockShape = RoundedCornerShape(
    topStart = 22.dp,
    topEnd = 14.dp,
    bottomEnd = 22.dp,
    bottomStart = 14.dp,
)

private val AppShapes = Shapes(
    extraSmall = SmallShape,
    small = SmallShape,
    medium = BlockShape,
    large = CardShape,
    extraLarge = CardShape,
)

// ----------------------------------------------------------------------------
// Type. Hierarchy comes from size and weight, nothing else.
//
// There was a serif display level here (FontFamily.Serif, which resolves to Noto Serif CJK on
// Chinese devices). It was dropped: on Android the serif CJK face is a *different family* from the
// system sans, so page titles and body text stopped looking like the same app — and it disagreed
// with the launcher, the settings app, and everything else on the phone. Default family now.
// ----------------------------------------------------------------------------

/**
 * The home list title. The only 38sp type in the app — the home screen is its cover.
 *
 * Bold. ExtraBold was tried and reverted: at 38sp the heavier weight closes up the CJK counters
 * and the title starts to look like a headline pasted on rather than the name of the screen.
 */
val DisplayStyle = TextStyle(
    fontSize = 38.sp,
    lineHeight = 42.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.035).em,
)

/**
 * The title on every screen except the home list.
 *
 * 21sp: one clear step above the 20dp back glyph it sits next to, without dwarfing it. The serif
 * face already gives the title its own voice, so it does not need to shout in size as well.
 */
val PageTitleStyle = TextStyle(
    fontSize = 21.sp,
    lineHeight = 27.sp,
    // ExtraBold, not Bold: the system CJK face at 21sp with Bold still reads a touch light next to
    // the 20dp icon glyphs. 800 gives the title the weight it needs without growing the size.
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.015).em,
)

/** The 21sp serif line in an empty state. */
val EmptyTitleStyle = TextStyle(
    fontSize = 21.sp,
    lineHeight = 30.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.01).em,
)

private val AppTypography = Typography().let { base ->
    base.copy(
        // bodyLarge is the item text: --fs-body 16 / --lh-body 23, tracking slightly tight.
        bodyLarge = TextStyle(fontSize = 16.5.sp, lineHeight = 22.sp, letterSpacing = (-0.01).em),
        bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
        // bodySmall is the timestamp row: --fs-meta 12 / --lh-meta 16.
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
        titleLarge = PageTitleStyle,
        titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        // labelLarge is the segmented tab / section label: --fs-ui 14 / --lh-ui 20.
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.14.em,
        ),
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
