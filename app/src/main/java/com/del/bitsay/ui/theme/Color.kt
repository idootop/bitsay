package com.del.bitsay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ----------------------------------------------------------------------------
// Raw values. Screens never use these directly — they read the theme-aware
// accessors below, which follow the light/dark scheme.
//
// Transcribed one-for-one from design/css/tokens.css, which is the single source of truth:
// if a value changes there it must change here in the same commit.
//
// The palette has exactly one "loud" element — accent — and it is not a hue. Black in light mode
// and white in dark mode, keeping the maximum-contrast role in both. The only other colour in the
// whole app is [BitSayPalette.leaf], and it is reserved for the plant in the empty states.
// ----------------------------------------------------------------------------

// Deepened from #EDF0F8. At 1.14:1 against the white cards the floor barely registered, and the
// whole screen read as one pale wash — the card structure only appeared once you looked for it.
private val CanvasLight = Color(0xFFE6E9F2)
private val SurfaceLight = Color(0xFFFFFFFF)
/**
 * A finished row keeps the **same white card** as an open one.
 *
 * Three tints were tried — #E7EAF2, then #EFF1F7 — and all of them read as "washed out" next to the
 * crisp white rows; a grey card with grey struck-through text looks disabled rather than done.
 *
 * The dark theme keeps its own step (#171923): dimming a card into a dark floor reads as *receding*,
 * which is the effect the grey was going for and never achieved on white. So the state is carried by
 * the tick's check mark, the strike-through and [InkSoft] text — not by the surface.
 */
private val CardDoneLight = SurfaceLight
private val InkLight = Color(0xFF191B26)
private val Ink2Light = Color(0xFF666B80)
// Was #9CA1B5: only 2.57:1 on a white card, below the 3:1 that a UI component (the tick ring)
// needs to be reliably visible. The floor and the placeholder text both use this.
private val Ink3Light = Color(0xFF8A90A6)
private val LineLight = Color(0xFFE8EAF3)
private val LeafLight = Color(0xFF8CA487)
private val AccentLight = Color(0xFF000000)
private val AccentInkLight = Color(0xFFFFFFFF)
private val AccentSoftLight = Color(0xFFEFF1F6)

private val CanvasDarkValue = Color(0xFF101119)
private val SurfaceDarkValue = Color(0xFF1B1D29)
private val CardDoneDarkValue = Color(0xFF171923)
private val InkDarkValue = Color(0xFFF2F3F8)
private val Ink2DarkValue = Color(0xFFA2A7BC)
private val Ink3DarkValue = Color(0xFF6E7387)
private val LineDarkValue = Color(0xFF282B3A)
private val LeafDarkValue = Color(0xFF5D7358)
private val AccentDarkValue = Color(0xFFFFFFFF)
private val AccentInkDarkValue = Color(0xFF111318)
private val AccentSoftDarkValue = Color(0xFF2A2D3A)

/**
 * Everything the screens need that Material 3 does not name.
 *
 * Provided by [BitSayTheme] so a colour never has to be chosen at a call site — and, more
 * importantly, so no call site can invent a second accent.
 */
@Immutable
data class BitSayPalette(
    /** The only loud element: FAB, tick, selected pill. Black in light, white in dark. */
    val accent: Color,
    /** Text/icons drawn on [accent]. */
    val accentInk: Color,
    /** Neutral wash behind selected rows. */
    val accentSoft: Color,
    /** Every list row. Uniform by design — a rotating pastel was tried and read as confetti. */
    val card: Color,
    val cardDone: Color,
    /** Sheet / dialog / widget surface. */
    val paper: Color,
    val background: Color,
    val ink: Color,
    /** Timestamps, hints. */
    val inkSoft: Color,
    /** Placeholders, unselected tabs, the faint tick ring. */
    val inkFaint: Color,
    val line: Color,
    /** The plant drawing in the empty states. Nothing else may use this. */
    val leaf: Color,
    /** Destructive actions only — the confirm button on a delete dialog. Never decoration. */
    val danger: Color,
    /**
     * A widget list row — the raised surface, and it has to be raised in **both** themes.
     *
     * Pure white in light, and the app's dark card colour in dark. Pure black was tried for dark and
     * reverted: against a dark tile it read as a hole, not as a row. The contrast ratio was the same
     * either way (1.12:1), which is the trap — the number was fine and the direction was backwards.
     * In a dark UI surfaces get *lighter* as they come forward, so a row darker than its tile reads
     * as something cut into the widget.
     *
     * The pure black belongs to the tile instead; see `widget_bg_dark.xml`.
     */
    val widgetRow: Color,
    /**
     * The seam between the two panes of the wide layout.
     *
     * Not [line]: that one is a hairline *on a white card*, and it disappears against [background]
     * (1.05:1). This is a translucent [inkFaint], which is the only tone the canvas is allowed to
     * carry — same family as the scrollbar, and it is deliberately weaker than it: a pane seam
     * should be noticed only when you look for it.
     */
    val divider: Color,
)

internal val LightPalette = BitSayPalette(
    accent = AccentLight,
    accentInk = AccentInkLight,
    accentSoft = AccentSoftLight,
    card = SurfaceLight,
    cardDone = CardDoneLight,
    paper = SurfaceLight,
    background = CanvasLight,
    ink = InkLight,
    inkSoft = Ink2Light,
    inkFaint = Ink3Light,
    line = LineLight,
    leaf = LeafLight,
    danger = Color(0xFFD23B2E),
    widgetRow = Color(0xFFFFFFFF),
    divider = Color(0x578A90A6),
)

internal val DarkPalette = BitSayPalette(
    // The accent flips to white rather than staying black: on a dark page the loudest thing has to
    // be the lightest, or the FAB stops being the thing your eye lands on first.
    accent = AccentDarkValue,
    accentInk = AccentInkDarkValue,
    accentSoft = AccentSoftDarkValue,
    card = SurfaceDarkValue,
    cardDone = CardDoneDarkValue,
    paper = SurfaceDarkValue,
    background = CanvasDarkValue,
    ink = InkDarkValue,
    inkSoft = Ink2DarkValue,
    inkFaint = Ink3DarkValue,
    line = LineDarkValue,
    leaf = LeafDarkValue,
    danger = Color(0xFFFF6B5C),
    widgetRow = SurfaceDarkValue,
    divider = Color(0x6B6E7387),
)

internal val LocalPalette = staticCompositionLocalOf { LightPalette }

/**
 * The palette for a resolved light/dark flag. Not composable on purpose: the widget renders from
 * a broadcast receiver and needs the same colours as the app.
 */
fun paletteFor(dark: Boolean): BitSayPalette = if (dark) DarkPalette else LightPalette

// ----------------------------------------------------------------------------
// Theme-aware accessors.
// ----------------------------------------------------------------------------

val Accent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
val AccentInk: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentInk
val AccentSoft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentSoft
val Card: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.card
val CardDone: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.cardDone
val Paper: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.paper
val Background: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val InkSoft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.inkSoft
val InkFaint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.inkFaint
val Line: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.line
val Leaf: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.leaf
val Danger: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.danger
val WidgetRow: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.widgetRow

/** The pane seam of the wide layout. See [BitSayPalette.divider] for why it is not [Line]. */
val Divider: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.divider

/** Material's own scheme, re-exported so screens have one import for all colours. */
val Scheme @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
