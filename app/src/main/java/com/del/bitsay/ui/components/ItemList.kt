package com.del.bitsay.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.del.bitsay.ui.theme.SPROUT_DURATION_MS
import com.del.bitsay.ui.theme.SPROUT_STAGGER_MS
import com.del.bitsay.ui.theme.SproutEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.del.bitsay.core.model.Item
import com.del.bitsay.core.util.TextPreview
import com.del.bitsay.core.util.TimeText
import com.del.bitsay.core.util.TimeWording
import com.del.bitsay.i18n.rememberTimeWording
import com.del.bitsay.ui.theme.Accent
import com.del.bitsay.ui.theme.AccentInk
import com.del.bitsay.ui.theme.Card
import com.del.bitsay.ui.theme.CardDone
import com.del.bitsay.ui.theme.CardShape
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.InkFaint
import com.del.bitsay.ui.theme.InkSoft

/**
 * The one and only item list. The home list and the search results both render through here, so a
 * row looks and behaves the same wherever it shows up — there is no second, "lighter" row design
 * to keep in sync.
 *
 * Every row is the **same white card**. A rotating pastel palette was tried and dropped: on a clean
 * floor six colours per screen read as confetti, and the one colour that is supposed to mean
 * something (the accent) stopped standing out.
 *
 * Click semantics stay with the caller because they differ per screen: the home list turns a tap
 * into "select" while picking rows, search always opens the editor.
 */
@Composable
fun ItemList(
    items: List<Item>,
    onClick: (Item) -> Unit,
    onToggleDone: (Item) -> Unit,
    modifier: Modifier = Modifier,
    selecting: Boolean = false,
    selection: Set<Long> = emptySet(),
    onLongClick: ((Item) -> Unit)? = null,
    /** Room at the tail of the list for whatever floats over it — the FAB on the home screen. */
    bottomPadding: Dp = FAB_CLEARANCE,
    /**
     * Stagger positions for rows that appeared **together**, keyed by id: 0, 1, 2 … in the order the
     * eye meets them.
     *
     * This is only about *timing*. Every row animates when it is first composed — see [ItemCard] —
     * so a row that scrolls into view gets the same entrance as the first row on screen, just
     * without a delay. The map exists so a batch arriving at once (the first fill, or what a save
     * produced) comes up like seedlings instead of in unison.
     *
     * A row removes its own entry the first time it is composed: the delay belongs to "this batch
     * just appeared", not to the row, and scrolling back must not make it wait again. Plain
     * MutableMap, not a state map — the removal happens during composition and must not schedule
     * another.
     *
     * Empty by default: search results pass nothing, so their rows animate in unison. The board does
     * the same (design/js/pages/search.js passes no `enter` flag).
     */
    entering: MutableMap<Long, Int> = mutableMapOf(),
) {
    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            // No top padding: the gap above the first row is the tab bar's bottom margin now, since
            // that is the element it actually separates the list from.
            //
            // The bottom one stays here, and the container is NOT padded. The container has to fill
            // the rest of the screen so the FAB floats *over* the list; the clearance is what lets
            // the last row scroll up past the FAB instead of being trapped underneath it.
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            ItemCard(
                item = item,
                selecting = selecting,
                selected = item.id in selection,
                onClick = { onClick(item) },
                onLongClick = onLongClick?.let { { it(item) } },
                onToggleDone = { onToggleDone(item) },
                // Consumed here, on the row's first composition. Anywhere later and the composable
                // has already been built with the old value.
                stagger = entering.remove(item.id),
            )
        }
    }
        // Inset from the screen edge: the cards already stop 20dp in, and a bar flush against
        // the glass reads as a clipped edge rather than as a scroll position.
        ScrollIndicator(listState, Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
    }
}

/**
 * A thin thumb down the right edge, matching the widget's scrollbar (`widget_scrollbar.xml`):
 * 3dp wide, rounded, and only there while the list is actually moving.
 *
 * Compose has no built-in scrollbar, so it is drawn from [LazyListState.layoutInfo]. The colour is
 * `inkFaint` rather than the widget's `line`: the widget's bar sits on a white card, this one sits
 * on the grey canvas, where a `line`-coloured bar is invisible.
 */
@Composable
private fun ScrollIndicator(state: LazyListState, modifier: Modifier = Modifier) {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo
    // Nothing to indicate when the whole list already fits.
    if (total == 0 || visible.isEmpty() || visible.size >= total) return

    // 60%, the same as the widget's scrollbar (#999CA1B5). At 50% it was legible but the two lists
    // sat at visibly different weights for no reason.
    val thumb = InkFaint.copy(alpha = 0.6f)
    val showing = remember { mutableStateOf(false) }
    // Same fade rhythm as the widget: 1200ms of stillness, then a 500ms fade.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .collectLatest { scrolling ->
                if (scrolling) showing.value = true else { delay(1200); showing.value = false }
            }
    }
    val fade by animateFloatAsState(if (showing.value) 1f else 0f, tween(500), label = "scrollbar")
    // Read during composition, not inside the draw lambda. A state read only in the draw phase is
    // not reliably observed here, so the animation ran while the canvas kept painting its first
    // frame — the bar stayed invisible even though its alpha was 1.
    val alpha = fade

    Canvas(modifier.fillMaxHeight().width(12.dp)) {
        if (alpha <= 0.01f) return@Canvas
        val first = visible.first().index
        val last = visible.last().index
        val fraction = (last - first + 1).toFloat() / total
        val track = size.height
        val thumbHeight = (track * fraction).coerceIn(28.dp.toPx(), track)
        val maxOffset = (track - thumbHeight).coerceAtLeast(0f)
        val maxFirst = (total - visible.size).coerceAtLeast(1)
        val top = maxOffset * (first.toFloat() / maxFirst).coerceIn(0f, 1f)
        val w = 3.dp.toPx()
        drawRoundRect(
            color = thumb.copy(alpha = thumb.alpha * alpha),
            topLeft = Offset(size.width - w - 2.dp.toPx(), top),
            size = Size(w, thumbHeight),
            cornerRadius = CornerRadius(w / 2f),
        )
    }
}

@Composable
private fun ItemCard(
    item: Item,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onToggleDone: () -> Unit,
    /** Position in the batch that just appeared, or null if this row was not part of one. */
    stagger: Int?,
) {
    val wording: TimeWording = rememberTimeWording()

    /*
     * The entrance: one animation, played by every row, once per composition.
     *
     * Earlier versions split this in two — a full sprout for rows a save had just produced, and a
     * quieter one for rows arriving by scroll. That was backwards: the rows arriving at the bottom
     * are the ones being looked at, so the further you scrolled the less the list moved. Same
     * animation for both now.
     *
     * All three properties run off one progress value, because the board's keyframes resolve at
     * different moments: opacity is already 1 at 60%, while the rise and the scale keep settling
     * until the end.
     *
     * `stagger` decides only *when* it starts. It is null for a row that arrives by scrolling, and
     * that row starts immediately — a delay there would hold it at alpha 0 for the length of the
     * delay, which is what used to flash blank cards during a fast fling.
     *
     * `remember` with no keys is what makes it a one-shot: a row that is already composed is not
     * rebuilt, so it cannot replay. A row that scrolls out and back is a new composition and plays
     * again — deliberate, and cheap: 460ms of transform on a handful of rows.
     */
    val grow = remember { Animatable(0f) }
    var animating by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        grow.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SPROUT_DURATION_MS,
                delayMillis = (stagger ?: 0) * SPROUT_STAGGER_MS,
                easing = SproutEasing,
            ),
        )
        // Drop the layer once every property is identity: an off-screen layer per row is not free,
        // and removing it at t=1 cannot be seen.
        animating = false
    }

    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (!animating) {
                    Modifier
                } else {
                    Modifier.graphicsLayer {
                        val t = grow.value
                        alpha = (t / 0.6f).coerceIn(0f, 1f)
                        translationY = (1f - t) * 14.dp.toPx()
                        val scale = 0.955f + 0.045f * t
                        scaleX = scale
                        scaleY = scale
                    }
                },
            )
            .clip(CardShape)
            .background(if (item.done) CardDone else Card)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 17.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Pick(selected)
            Spacer(Modifier.width(13.dp))
        }
        // The tick doubles as the "mark this todo done" button, which is meaningless while rows are
        // being picked — and two circles side by side read as clutter. The struck-through, greyed
        // text still says the todo is done.
        if (item.isTodo && !selecting) {
            Box(
                Modifier
                    .size(23.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleDone,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Tick(done = item.done)
            }
            Spacer(Modifier.width(13.dp))
        }
        Column(Modifier.weight(1f)) {
            // One line only, same as the widget: the list is for scanning, the editor is for
            // reading. Long entries are cut off with an ellipsis.
            Text(
                text = TextPreview.singleLine(item.text),
                style = MaterialTheme.typography.bodyLarge,
                // InkSoft, not InkFaint: with the card keeping its white background the text is
                // what says "done", and InkFaint is 3.17:1 here — under the 4.5:1 body text needs.
                color = if (item.done) InkSoft else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
            )
            Text(
                text = TimeText.relative(item.createdAt, wording),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

/**
 * The done circle — the same drawing the widget uses (`ic_todo_open` / `ic_todo_done`).
 *
 * Open is a bare ring; done is the same ring with a check inside. Both in [InkFaint]: the state is
 * carried by the check being there, not by the circle changing colour or filling in.
 *
 * It used to be a solid accent disc with a knocked-out check, which in dark mode meant a field of
 * white discs — six finished todos shouting louder than the one still open. The widget had it right
 * all along; this is the app catching up.
 */
@Composable
private fun Tick(done: Boolean, size: Dp = 23.dp) {
    val ink = InkFaint
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = 1.5.dp.toPx()
        // The ring is inset by half a stroke so it is not clipped at the bounds.
        drawCircle(ink, radius = w / 2f - stroke / 2f, style = Stroke(width = stroke))
        if (done) {
            val check = Path().apply {
                moveTo(w * 0.29f, w * 0.52f)
                lineTo(w * 0.44f, w * 0.67f)
                lineTo(w * 0.72f, w * 0.35f)
            }
            drawPath(check, color = ink, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Multi-select circle. Same ring weight as [Tick] so the two never look like different systems. */
@Composable
private fun Pick(selected: Boolean, size: Dp = 22.dp) {
    val ring = InkFaint
    val fill = Accent
    val glyph = AccentInk
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        if (selected) {
            drawCircle(fill, radius = r)
            val w = this.size.width
            val check = Path().apply {
                moveTo(w * 0.28f, w * 0.52f)
                lineTo(w * 0.44f, w * 0.67f)
                lineTo(w * 0.73f, w * 0.34f)
            }
            drawPath(
                check,
                color = glyph,
                style = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else {
            drawCircle(ring, radius = r - 0.75.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}
