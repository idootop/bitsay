package com.del.bitsay.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.del.bitsay.R
import com.del.bitsay.core.model.Item
import com.del.bitsay.core.util.TextPreview
import com.del.bitsay.core.util.TimeText
import com.del.bitsay.core.util.TimeWording
import com.del.bitsay.i18n.rememberTimeWording
import com.del.bitsay.ui.theme.CardColors
import com.del.bitsay.ui.theme.CuteShape
import com.del.bitsay.ui.theme.Done
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.InkSoft
import com.del.bitsay.ui.theme.TodoCardColors

/**
 * The one and only item list. The home list and the search results both render through here, so a
 * row looks and behaves the same wherever it shows up — there is no second, "lighter" row design
 * to keep in sync.
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
    bottomPadding: Dp = 104.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 12.dp,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            ItemCard(
                item = item,
                index = index,
                selecting = selecting,
                selected = item.id in selection,
                onClick = { onClick(item) },
                onLongClick = onLongClick?.let { { it(item) } },
                onToggleDone = { onToggleDone(item) },
            )
        }
    }
}

/**
 * A note or todo as one card. Notes and todos share it: only the colour family and the leading
 * done circle differ.
 */
@Composable
private fun ItemCard(
    item: Item,
    index: Int,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onToggleDone: () -> Unit,
) {
    val wording: TimeWording = rememberTimeWording()
    val background = when {
        item.done -> Done
        item.isTodo -> TodoCardColors[index % TodoCardColors.size]
        else -> CardColors[index % CardColors.size]
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CuteShape)
            .background(background)
            .border(1.5.dp, Ink.copy(alpha = 0.13f), CuteShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            androidx.compose.material3.Icon(
                painter = painterResource(
                    if (selected) R.drawable.ic_selected else R.drawable.ic_unselected,
                ),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        // The done circle doubles as the "tick this todo" button, which is meaningless while rows
        // are being picked — and two circles side by side read as clutter. The struck-through,
        // greyed text still says the todo is done.
        if (item.isTodo && !selecting) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleDone,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    painter = painterResource(
                        if (item.done) R.drawable.ic_todo_done else R.drawable.ic_todo_open,
                    ),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            // One line only, same as the widget: the list is for scanning, the editor is for
            // reading. Long entries are cut off with an ellipsis.
            Text(
                text = TextPreview.singleLine(item.text),
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.done) InkSoft else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
            )
            Text(
                text = TimeText.relative(item.createdAt, wording),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
