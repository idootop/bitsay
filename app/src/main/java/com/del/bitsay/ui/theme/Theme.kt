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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
// Responsive metrics. Taken straight from the "宽屏" block of tokens.css.
//
// Two panes instead of one, once the window is at least as wide as a small tablet. Below that —
// every phone in portrait, and a folded cover screen — nothing here applies and the app stays the
// single column it has always been.
//
// 600dp is not a number I picked: it is the floor at which "344dp list + a readable detail" still
// fits. The list pane clamps to 45% of the window so the seam never eats the editor alive on the
// narrow side of the threshold, and the detail column clamps at [MeasureWidth] so a 1400dp desktop
// window does not stretch a paragraph across the whole screen.
// ----------------------------------------------------------------------------

/** The window width at which the list and the detail start sitting side by side. */
val WideBreakpoint = 600.dp

/** Preferred width of the list pane — `--pane-list`. Shrinks to 45% of the window when needed. */
val PaneListWidth = 344.dp

/** Longest line of running text the detail pane will produce — `--measure`. */
val MeasureWidth = 720.dp

/** Width actually given to the list pane in a window of [windowWidth]. */
fun listPaneWidth(windowWidth: Dp): Dp = minOf(PaneListWidth, windowWidth * 0.45f)

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

// ----------------------------------------------------------------------------
// Motion. Mirrors design/css/components.css — `@keyframes sprout` and its timing.
// ----------------------------------------------------------------------------

/** `cubic-bezier(.16,.9,.3,1)`: leaves fast, settles slowly. */
val SproutEasing = CubicBezierEasing(0.16f, 0.9f, 0.3f, 1f)

/** `animation: sprout .46s`. */
const val SPROUT_DURATION_MS = 460

/** `animation-delay: calc(var(--i) * 26ms)` — a row of new items comes up like seedlings. */
const val SPROUT_STAGGER_MS = 26

/**
 * How many rows may share one stagger.
 *
 * The board staggers every row, but it renders the whole list at once. Here the list is a
 * LazyColumn, so a row's position in the list is not its position in time: the 20th row is composed
 * when it scrolls into view, and staggering it by its list index would hold it at alpha 0 for 520ms
 * first — that is what made fast scrolling flash blank cards.
 *
 * So the stagger is capped to a screenful, and it only applies to rows that appear **together**
 * (the first fill, or a batch a data change just produced). A row that arrives later by scrolling
 * gets the same animation with no delay.
 */
const val SPROUT_MAX_ROWS = 10
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
