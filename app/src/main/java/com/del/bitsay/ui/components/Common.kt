package com.del.bitsay.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.del.bitsay.R
import com.del.bitsay.ui.theme.BlockShape
import com.del.bitsay.ui.theme.Danger
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.del.bitsay.ui.theme.Accent
import com.del.bitsay.ui.theme.AccentInk
import com.del.bitsay.ui.theme.Background
import com.del.bitsay.ui.theme.CardShape
import com.del.bitsay.ui.theme.EmptyTitleStyle
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.InkFaint
import com.del.bitsay.ui.theme.InkSoft
import com.del.bitsay.ui.theme.Leaf
import com.del.bitsay.ui.theme.Line
import com.del.bitsay.ui.theme.Paper

/**
 * The page floor: one flat colour.
 *
 * It was a three-stop gradient (sky -> canvas -> moss) with a soft light bleeding in from the top.
 * Dropped for two reasons: the greenish `moss` stop at the bottom read as a stain rather than as
 * depth, and a page whose colour drifts vertically fights the flat white cards sitting on it —
 * a card at the top and a card at the bottom ended up on visibly different backgrounds.
 */
@Composable
fun PaperBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Background)) {
        content()
    }
}

/**
 * The card shape used for rows. Flat white on the grey floor — no outline and no shadow.
 *
 * The old card carried a 1.5dp hand-drawn border. With the palette reduced to black/white/grey the
 * border became the loudest thing on the screen, and it fought the FAB for attention.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(horizontal = 17.dp, vertical = 15.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(color)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}

/** A surface that is separated from the page by a hairline rather than by a shadow. */
@Composable
fun Modifier.hairline(shape: androidx.compose.ui.graphics.Shape = CardShape): Modifier =
    border(1.dp, Line, shape)

/** 40dp round icon button, tinted like the design's `.icon-btn` (ink-2, going to ink on press). */
@Composable
fun RoundIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color = Color.Transparent,
    tint: Color = InkSoft,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.525f),
        )
    }
}

/**
 * Pill switch between notes and todos.
 *
 * The indicator is a single sliding pill rather than a background on each item, because it has to
 * track a pager: [position] is the pager's continuous page offset (0 = first, 1 = second), so the
 * pill travels with the finger during a drag instead of jumping when the pager settles.
 *
 * Pass `position = null` where there is no pager behind the tabs; the pill then snaps to [selected].
 *
 * The trough is a translucent wash of the ink colour rather than a fixed grey, so one value reads
 * correctly on both the light and the dark floor.
 */
@Composable
fun <T> SegmentedTabs(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    position: Float? = null,
) {
    val trough = Ink.copy(alpha = 0.06f)
    BoxWithConstraints(
        modifier
            .clip(CircleShape)
            .background(trough)
            .padding(4.dp),
    ) {
        // No gap between items: the pill's offset is a plain multiple of the segment width, and a
        // gap would make the travel distance depend on the index.
        val segment = maxWidth / options.size
        val at = (position ?: options.indexOf(selected).toFloat())
            .coerceIn(0f, (options.size - 1).toFloat())

        Box(
            Modifier
                .offset(x = segment * at)
                .width(segment)
                .height(40.dp)
                .clip(CircleShape)
                .background(Accent),
        )
        Row(Modifier.height(40.dp)) {
            options.forEach { option ->
                val active = option == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        // A crossfade rather than a hard swap: mid-drag both labels are half-lit, so
                        // switching on `selected` alone would blink one of them.
                        color = if (at > 0.5f == (options.indexOf(option) == 1)) AccentInk else InkFaint,
                    )
                }
            }
        }
    }
}

/**
 * A two-action confirmation. Deleting has no undo here, so both the editor's delete and the batch
 * delete go through this.
 *
 * Shaped like the settings choice dialog on purpose: the same quiet cancel on the right. The
 * destructive action is the one place in the app allowed a second colour — it is red, because
 * "delete" is the single action here that cannot be undone, and accent-coloured text made it look
 * exactly like every other confirm.
 */
@Composable
fun ConfirmDialog(
    title: String,
    hint: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(BlockShape)
                .background(Paper)
                .padding(top = 20.dp, bottom = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = Ink,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp),
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                color = InkSoft,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSoft,
                    )
                }
                TextButton(onClick = onConfirm) {
                    Text(
                        text = confirmLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Danger,
                    )
                }
            }
        }
    }
}

/** Small caps group label above a settings block. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = 2.dp, top = 22.dp, bottom = 9.dp),
        style = MaterialTheme.typography.labelSmall,
        color = InkFaint,
    )
}

/**
 * Row helper used by the settings list.
 *
 * The proportions are the point. A 30dp icon box next to a 17sp title over a 12sp line made the
 * icon look like it was floating: the text block was 40dp tall and the glyph only 19dp. Here the
 * icon box grows to 32dp and the type steps down, so the two halves of the row carry similar
 * visual weight instead of the text winning.
 */
@Composable
fun SettingRow(
    painter: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 17.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides Ink) {
                Icon(painter, contentDescription = null, modifier = Modifier.size(19.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = Ink,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = InkFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        trailing?.invoke(this)
    }
}

/** A non-interactive row: same metrics as [SettingRow] so a block never mixes two rhythms. */
@Composable
fun InfoRow(painter: Painter, title: String, subtitle: String) {
    SettingRow(painter = painter, title = title, subtitle = subtitle, onClick = {})
}

/**
 * Renders `**like this**` as bold. Empty-state hints emphasise the one thing to press ("Tap **+**");
 * a full annotated-string builder at every call site would be far more code than the feature is
 * worth, and the marker survives translation as long as translators keep it.
 */
@Composable
internal fun emphasized(text: String, boldColor: Color): AnnotatedString {
    if (!text.contains("**")) return AnnotatedString(text)
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val open = text.indexOf("**", i)
            if (open < 0) {
                append(text.substring(i)); break
            }
            append(text.substring(i, open))
            val close = text.indexOf("**", open + 2)
            if (close < 0) {
                append(text.substring(open)); break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                append(text.substring(open + 2, close))
            }
            i = close + 2
        }
    }
}


// The 破土 mark in its own colours. Same geometry as res/drawable/ic_launcher_foreground.xml,
// drawn here so it can sit directly on the page instead of on an ink plate.
private val BRAND_SOIL = Color(0xFF2C4A35)
private val BRAND_SPROUT = Color(0xFF7FC98F)

/**
 * The app mark: a seedling breaking out of split soil. Used as the home screen's title.
 *
 * The two greens are the icon's own values, not the palette's `leaf`: they are tuned to read at
 * small sizes and against a light floor, and reusing the muted in-app leaf here made the mark look
 * like it had faded.
 */
@Composable
fun BrandMark(height: Dp = 46.dp, modifier: Modifier = Modifier) {
    // The artwork occupies x 34..74, y 29..79 of the icon's 108x108 viewBox: 40 wide, 50 tall.
    Canvas(modifier.size(width = height * 0.8f, height = height)) {
        val s = this.size.height / 50f
        val ox = this.size.width / 2f - 54f * s
        val oy = -29f * s
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)

        // These three keep the bodies below readable as the icon's own coordinates, so they can be
        // checked line-by-line against res/drawable/ic_launcher_foreground.xml.
        fun Path.m(x: Float, y: Float) = p(x, y).let { moveTo(it.x, it.y) }
        fun Path.l(x: Float, y: Float) = p(x, y).let { lineTo(it.x, it.y) }
        fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val a = p(x1, y1); val b = p(x2, y2); val d = p(x3, y3)
            cubicTo(a.x, a.y, b.x, b.y, d.x, d.y)
        }

        // The two soil lobes are NOT the same shape, and the difference is not cosmetic:
        //
        //   left   M34,79   C34,71 41,67 50,66   L52.5,79  Z    -- curve, then a straight edge
        //   right  M57.5,79 L60,66  C69,67 74,71 74,79   Z      -- straight edge FIRST, then curve
        //
        // An earlier version ran both through one "moveTo, cubicTo, lineTo, close" helper. That is
        // right for the left lobe and wrong for the right one: it curved away from the bottom vertex
        // without ever touching (60,66), which is the corner that went missing in Settings.
        val soilLeft = Path().apply {
            m(34f, 79f); c(34f, 71f, 41f, 67f, 50f, 66f); l(52.5f, 79f); close()
        }
        val soilRight = Path().apply {
            m(57.5f, 79f); l(60f, 66f); c(69f, 67f, 74f, 71f, 74f, 79f); close()
        }
        val leafLeft = Path().apply {
            m(54f, 46f); c(44f, 47f, 37f, 39f, 37f, 29f); c(47f, 29f, 54f, 36f, 54f, 46f); close()
        }
        val leafRight = Path().apply {
            m(54f, 46f); c(64f, 47f, 71f, 39f, 71f, 29f); c(61f, 29f, 54f, 36f, 54f, 46f); close()
        }

        drawPath(soilLeft, BRAND_SOIL)
        drawPath(soilRight, BRAND_SOIL)
        // The stem is the only stroked part; everything else is filled.
        drawPath(
            Path().apply { m(54f, 79f); c(53f, 68f, 53f, 57f, 54f, 46f) },
            color = BRAND_SPROUT,
            style = Stroke(width = 4.4f * s, cap = StrokeCap.Round),
        )
        drawPath(leafLeft, BRAND_SPROUT)
        drawPath(leafRight, BRAND_SPROUT)
    }
}

/**
 * The app icon, drawn to match what the launcher actually shows.
 *
 * Not `painterResource(R.mipmap.ic_launcher)`: an AdaptiveIconDrawable carries no mask, so drawn
 * directly it comes out as the full 108x108 square with the artwork floating in the middle of a
 * lot of margin — nothing like the icon on the home screen.
 *
 * The geometry that matters is the **visible area**: an adaptive icon only guarantees the central
 * 72x72 of its 108x108 canvas, and that is the region launchers crop to and then mask. The artwork
 * is 40x50 centred in the canvas, so on the home screen it reads as 50/72 = 69.4% of the icon's
 * height. Drawing the mark at 66% (the earlier value) made it visibly smaller here than on the
 * launcher, which is what this fixes.
 */
private const val ADAPTIVE_VISIBLE = 72f / 108f
private const val MARK_HEIGHT_IN_CANVAS = 50f / 108f

@Composable
fun AppIconPlate(size: Dp = 52.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            // ~22% is the squircle radius a launcher applies.
            .clip(RoundedCornerShape(size * 0.22f))
            .background(Color(0xFF17140F)),
        contentAlignment = Alignment.Center,
    ) {
        BrandMark(height = size * (MARK_HEIGHT_IN_CANVAS / ADAPTIVE_VISIBLE))
    }
}

/**
 * Empty state: a plant, a serif line, and a hint.
 *
 * Text-first on purpose. A mascot was tried twice (a folded note, then a hamster) and rejected —
 * an app about plain text does not need a character. A botanical line drawing is a *still life*:
 * no face, no interaction, and it never appears anywhere except here.
 *
 * It has two forms. As drawn it is ~300dp of content, and the list's floating **+** owns the bottom
 * 108dp of its corner, so the drawn form needs a window about 400dp tall — see
 * [EmptyStateRoomyHeight]. A landscape phone, a split-screen half and a folded cover screen all
 * give it less than that, and being sheared off at the bottom is how you end up with a plant and no
 * sentence. Below the threshold it drops to plant + headline, **centred**.
 *
 * Centring is what makes the short form safe next to the button, and it is why the short form needs
 * no bottom reservation: the content is a ~40dp plant over a ~104dp headline, both centred, so it
 * stops around x=112dp in a 344dp pane while the FAB starts at x=256dp. They cannot meet. Reserving
 * the corner as well — the first attempt — squeezed the headline to zero height in a 170dp page,
 * which is a worse failure than the one it was guarding against.
 */
/**
 * How much of the bottom the floating **+** occupies, measured from the bottom edge of a list.
 *
 * The list scrolls *under* the button, so it pads its content by this much — see
 * [com.del.bitsay.ui.components.ItemList]'s `bottomPadding`.
 */
val FAB_CLEARANCE = 108.dp

/**
 * The height below which [EmptyState] switches to its short form.
 *
 * The drawn form is ~300dp of content (72 top pad, a 127dp plant, the headline, a two-line hint)
 * plus 64dp of breathing room at the bottom — and the list's floating **+** owns the last 108dp of
 * its corner, so the content has to finish above that line. ~400dp is where both fit.
 */
private val EmptyStateRoomyHeight = 400.dp

@Composable
fun EmptyState(
    title: String,
    /**
     * Optional. A hint earns its place only when it tells the reader something they cannot
     * already see — "试试点右下角的 +" when the button is right there and unlabelled. A line that
     * explains how the screen works ("searches notes only", "the list stays put on a wide screen")
     * is a note for whoever reviewed the layout, not for whoever is using it; those screens get
     * the title on its own.
     */
    hint: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 92.dp,
) {
    BoxWithConstraints(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val roomy = maxHeight >= EmptyStateRoomyHeight
        Column(
            modifier = Modifier
                .align(if (roomy) Alignment.TopCenter else Alignment.Center)
                .fillMaxWidth()
                .padding(
                    top = if (roomy) 72.dp else 0.dp,
                    bottom = if (roomy) 64.dp else 0.dp,
                    start = 20.dp,
                    end = 20.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Sprout(size = if (roomy) size else size * 0.45f)
            Text(
                text = title,
                style = EmptyTitleStyle,
                color = Ink,
                modifier = Modifier.padding(top = if (roomy) 20.dp else 10.dp),
            )
            if (roomy && hint != null) {
                Text(
                    text = emphasized(hint, Ink),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.5.sp, lineHeight = 21.sp),
                    color = InkSoft,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

// The seedling, in the drawing's own 64x88 coordinate space. Two leaves meet at ONE node: a vine
// puts its leaves at different heights along the stem, and that reads as climbing, not sprouting.
private const val SPROUT_VB_W = 64f
private const val SPROUT_VB_H = 88f
private val SPROUT_STEM = floatArrayOf(32f, 84f, 31f, 68f, 31f, 50f, 32f, 36f)
private val SPROUT_LEAF_L = floatArrayOf(32f, 36f, 22f, 37f, 13f, 29f, 13f, 16f, 24f, 16f, 32f, 25f, 32f, 36f)
private val SPROUT_LEAF_R = floatArrayOf(32f, 36f, 42f, 37f, 51f, 29f, 51f, 16f, 40f, 16f, 32f, 25f, 32f, 36f)

private fun sproutPath(v: FloatArray, scale: Float): Path = Path().apply {
    moveTo(v[0] * scale, v[1] * scale)
    var i = 2
    while (i + 5 < v.size) {
        cubicTo(
            v[i] * scale, v[i + 1] * scale,
            v[i + 2] * scale, v[i + 3] * scale,
            v[i + 4] * scale, v[i + 5] * scale,
        )
        i += 6
    }
    if (v.size == 12) close()
}

/**
 * The plant that draws itself.
 *
 * The stem grows first, then the two leaves open almost together (0 / 100 / 120 ms into a 320 ms
 * draw). Once drawn it stays drawn — a loop would read as "flashing", not "growing". The 1.4-degree
 * sway afterwards is the only thing that keeps moving.
 *
 * Timing has been cut twice on user feedback: 1150 ms felt like a loading spinner, and 640 ms still
 * felt slow. Two things matter here, not just the duration:
 *   * the whole thing has to finish before the eye settles, so it reads as "it was already there"
 *     rather than as something you are waiting for;
 *   * each stroke is **eased out**, so it shoots away from the base and then decelerates. A linear
 *     dash crawl is what actually read as slow — the drawn front barely moves at the start.
 */
@Composable
fun Sprout(size: Dp = 92.dp, modifier: Modifier = Modifier) {
    val leaf = Leaf
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 440f,
            animationSpec = tween(durationMillis = 440, easing = LinearEasing),
        )
    }
    val sway = rememberInfiniteTransition(label = "sprout")
    val angle = sway.animateFloat(
        initialValue = 0f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sway",
    )

    Canvas(
        modifier
            .size(width = size, height = size * SPROUT_VB_H / SPROUT_VB_W)
            .graphicsLayer {
                rotationZ = angle.value
                // The design sways about (32,84) in the 64x88 viewBox — the foot of the stem.
                transformOrigin = TransformOrigin(0.5f, 84f / SPROUT_VB_H)
            },
    ) {
        val scale = this.size.width / SPROUT_VB_W
        val stroke = Stroke(
            width = 2.1f * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val t = progress.value
        // (path, start delay in ms) — mirrors the CSS animation-delay trio.
        val parts = listOf(
            Triple(SPROUT_STEM, 0f, false),
            Triple(SPROUT_LEAF_L, 100f, true),
            Triple(SPROUT_LEAF_R, 120f, true),
        )
        parts.forEach { (verts, delay, closed) ->
            val p = sproutPath(verts, scale)
            val measure = PathMeasure().apply { setPath(p, false) }
            val len = measure.length
            val linear = ((t - delay) / 320f).coerceIn(0f, 1f)
            // Ease-out cubic: the stroke front leaves the base fast and settles at the tip.
            val f = 1f - (1f - linear) * (1f - linear) * (1f - linear)
            if (f <= 0f || len <= 0f) return@forEach
            val dest = Path()
            measure.getSegment(0f, len * f, dest, true)
            drawPath(dest, color = leaf, style = stroke)
        }
    }
}
