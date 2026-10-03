package com.del.bitsay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ----------------------------------------------------------------------------
// Raw values. Screens never use these directly — they read the theme-aware
// accessors at the bottom of this file, which follow the light/dark scheme.
// Kept in sync with res/values/colors.xml + res/values-night/colors.xml (the widget).
// ----------------------------------------------------------------------------

private val PaperLight = Color(0xFFFFFDF7)
private val BgLight = Color(0xFFFCF3E8)
private val InkLight = Color(0xFF3D3A38)
private val InkSoftLight = Color(0xFF8A8279)
private val LineLight = Color(0xFFE6DDCB)

private val PaperDarkValue = Color(0xFF2C2A27)
private val BgDarkValue = Color(0xFF211F1D)
private val InkDarkValue = Color(0xFFF2EDE4)
private val InkSoftDarkValue = Color(0xFFA79F94)
private val LineDarkValue = Color(0xFF443F39)

private val SunLight = Color(0xFFFFD34E)
private val MintLight = Color(0xFFB7E4C7)
private val SkyLight = Color(0xFFAEDCEB)
private val BlushLight = Color(0xFFFFC2C9)
private val LilacLight = Color(0xFFD9CCF0)
private val PeachLight = Color(0xFFFFD3B6)
private val DoneLight = Color(0xFFEDE9E1)

/**
 * Dark counterparts of the pastels: same hues, dropped to a luminance that does not glare on a
 * dark page. Reusing the light pastels would have looked like six holes punched in the screen.
 */
private val SunDarkValue = Color(0xFF6B5320)
private val MintDarkValue = Color(0xFF2F4F3C)
private val SkyDarkValue = Color(0xFF29454F)
private val BlushDarkValue = Color(0xFF52333A)
private val LilacDarkValue = Color(0xFF3E3654)
private val PeachDarkValue = Color(0xFF523D2C)
private val DoneDarkValue = Color(0xFF2A2825)

/**
 * Everything the screens need that Material 3 does not name: the pastel rotation and the two
 * accent colours. Provided by [BitSayTheme] so a colour never has to be chosen at a call site.
 */
@Immutable
data class BitSayPalette(
    val cards: List<Color>,
    val todoCards: List<Color>,
    val done: Color,
    val sun: Color,
    val mint: Color,
    val sky: Color,
    val lilac: Color,
    val paper: Color,
    val background: Color,
    val ink: Color,
    val inkSoft: Color,
    val line: Color,
)

internal val LightPalette = BitSayPalette(
    cards = listOf(SunLight, MintLight, SkyLight, BlushLight, LilacLight, PeachLight),
    todoCards = listOf(SkyLight, MintLight, BlushLight),
    done = DoneLight,
    sun = SunLight,
    mint = MintLight,
    sky = SkyLight,
    lilac = LilacLight,
    paper = PaperLight,
    background = BgLight,
    ink = InkLight,
    inkSoft = InkSoftLight,
    line = LineLight,
)

internal val DarkPalette = BitSayPalette(
    cards = listOf(SunDarkValue, MintDarkValue, SkyDarkValue, BlushDarkValue, LilacDarkValue, PeachDarkValue),
    todoCards = listOf(SkyDarkValue, MintDarkValue, BlushDarkValue),
    done = DoneDarkValue,
    // Accents are darkened too, not kept bright: in dark mode every card carries light text, and
    // a saturated yellow behind near-white text is unreadable.
    sun = SunDarkValue,
    mint = MintDarkValue,
    sky = SkyDarkValue,
    lilac = LilacDarkValue,
    paper = PaperDarkValue,
    background = BgDarkValue,
    ink = InkDarkValue,
    inkSoft = InkSoftDarkValue,
    line = LineDarkValue,
)

internal val LocalPalette = staticCompositionLocalOf { LightPalette }

/**
 * The palette for a resolved light/dark flag. Not composable on purpose: the widget renders from
 * a broadcast receiver and needs the same colours as the app.
 */
fun paletteFor(dark: Boolean): BitSayPalette = if (dark) DarkPalette else LightPalette

// ----------------------------------------------------------------------------
// Theme-aware accessors. These keep every call site unchanged while making the
// whole UI follow the light/dark scheme.
// ----------------------------------------------------------------------------

val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val InkSoft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.inkSoft
val Paper: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.paper
val Bg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
val Line: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.line

val Sun: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sun
val Mint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.mint
val Sky: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sky
val Lilac: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.lilac
val Done: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.done

/** Pastel card colours, cycled by list position. */
val CardColors: List<Color> @Composable @ReadOnlyComposable get() = LocalPalette.current.cards

/** Todo cards, kept to the cooler half of the palette so the list reads as "tasks". */
val TodoCardColors: List<Color> @Composable @ReadOnlyComposable get() = LocalPalette.current.todoCards

/** Material's own scheme, re-exported so screens have one import for all colours. */
val Scheme @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
