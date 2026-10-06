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

private val CanvasLight = Color(0xFFEDF0F8) // page floor: cool grey, one step under the white cards
private val SkyLight = Color(0xFFEFF4FC) // gradient top
private val MossLight = Color(0xFFE8EEE6) // gradient bottom
private val SurfaceLight = Color(0xFFFFFFFF)
private val CardDoneLight = Color(0xFFE7EAF2) // done: one step darker, never a colour
private val InkLight = Color(0xFF191B26)
private val Ink2Light = Color(0xFF666B80)
private val Ink3Light = Color(0xFF9CA1B5)
private val LineLight = Color(0xFFE8EAF3)
private val LeafLight = Color(0xFF8CA487)
private val AccentLight = Color(0xFF000000)
private val AccentInkLight = Color(0xFFFFFFFF)
private val AccentSoftLight = Color(0xFFEFF1F6)

private val SkyDarkValue = Color(0xFF131722)
private val CanvasDarkValue = Color(0xFF101119)
private val MossDarkValue = Color(0xFF101611)
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
    /** Top and bottom stops of the page gradient, with [background] in the middle. */
    val sky: Color,
    val moss: Color,
    /** Dawn light at the very top of the page. Fully transparent in dark mode: a 92% white wash
     *  over a near-black floor greys the whole screen out. */
    val glow: Color,
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
)

internal val LightPalette = BitSayPalette(
    accent = AccentLight,
    accentInk = AccentInkLight,
    accentSoft = AccentSoftLight,
    card = SurfaceLight,
    cardDone = CardDoneLight,
    paper = SurfaceLight,
    background = CanvasLight,
    sky = SkyLight,
    moss = MossLight,
    glow = Color(0xEBFFFFFF),
    ink = InkLight,
    inkSoft = Ink2Light,
    inkFaint = Ink3Light,
    line = LineLight,
    leaf = LeafLight,
    danger = Color(0xFFD23B2E),
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
    sky = SkyDarkValue,
    moss = MossDarkValue,
    glow = Color.Transparent,
    ink = InkDarkValue,
    inkSoft = Ink2DarkValue,
    inkFaint = Ink3DarkValue,
    line = LineDarkValue,
    leaf = LeafDarkValue,
    danger = Color(0xFFFF6B5C),
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
val Sky: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sky
val Moss: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.moss
val Glow: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.glow
val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val InkSoft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.inkSoft
val InkFaint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.inkFaint
val Line: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.line
val Leaf: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.leaf
val Danger: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.danger

/** Material's own scheme, re-exported so screens have one import for all colours. */
val Scheme @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
