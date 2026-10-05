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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    bottomPadding: Dp = 108.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 14.dp,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { _, item ->
            ItemCard(
                item = item,
                selecting = selecting,
                selected = item.id in selection,
                onClick = { onClick(item) },
                onLongClick = onLongClick?.let { { it(item) } },
                onToggleDone = { onToggleDone(item) },
            )
        }
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
) {
    val wording: TimeWording = rememberTimeWording()
    Row(
        Modifier
            .fillMaxWidth()
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
                color = if (item.done) InkFaint else Ink,
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
 * The done circle. Open = a hairline ring; done = a solid accent disc with a knocked-out check.
 *
 * Drawn rather than shipped as a vector so it can follow the accent, which flips black to white
 * between the light and dark schemes.
 */
@Composable
private fun Tick(done: Boolean, size: Dp = 23.dp) {
    val ring = InkFaint
    val fill = Accent
    val glyph = AccentInk
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        if (done) {
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
                style = Stroke(width = w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else {
            drawCircle(ring, radius = r - 0.75.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
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
