package com.del.bitsay.ui.screen

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.del.bitsay.R
import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind
import com.del.bitsay.core.util.TextPreview
import com.del.bitsay.core.util.TimeText
import com.del.bitsay.ui.AppUiState
import com.del.bitsay.ui.components.CuteIconButton
import com.del.bitsay.ui.components.EmptyHint
import com.del.bitsay.ui.components.SegmentedTabs
import com.del.bitsay.ui.theme.Blush
import com.del.bitsay.ui.theme.CardColors
import com.del.bitsay.ui.theme.CuteShape
import com.del.bitsay.ui.theme.CuteShapeSmall
import com.del.bitsay.ui.theme.Done
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.InkSoft
import com.del.bitsay.ui.theme.Mint
import com.del.bitsay.ui.theme.Sky
import com.del.bitsay.ui.theme.Sun

@Composable
fun ListScreen(
    state: AppUiState,
    onSelectTab: (Kind) -> Unit,
    onToggleSearch: () -> Unit,
    onQuery: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onToggleDone: (Long) -> Unit,
    onNew: () -> Unit,
    onBeginSelection: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSetSelectedDone: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Selection mode replaces the whole header rather than stacking on top of it: while
            // picking rows, "search / settings / which tab" are all irrelevant.
            if (state.inSelectionMode) {
                SelectionHeader(
                    state = state,
                    onSelectAll = onSelectAll,
                    onClearSelection = onClearSelection,
                    onDeleteSelected = onDeleteSelected,
                    onSetSelectedDone = onSetSelectedDone,
                )
            } else {
                Header(
                    state = state,
                    onToggleSearch = onToggleSearch,
                    onOpenSettings = onOpenSettings,
                )
                SegmentedTabs(
                    options = listOf(Kind.NOTE, Kind.TODO),
                    selected = state.tab,
                    label = { stringResource(if (it == Kind.NOTE) R.string.tab_notes else R.string.tab_todos) },
                    accent = { if (it == Kind.NOTE) Sun else Mint },
                    onSelect = onSelectTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                )
                if (state.searchOpen) {
                    SearchField(
                        value = state.query,
                        onValueChange = onQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
            val items = state.visible
            if (items.isEmpty()) {
                EmptyHint(
                    text = when {
                        state.query.isNotBlank() -> stringResource(R.string.empty_search)
                        state.tab == Kind.NOTE -> stringResource(R.string.empty_notes)
                        else -> stringResource(R.string.empty_todos)
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 12.dp,
                        bottom = 104.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        ItemCard(
                            item = item,
                            index = index,
                            selecting = state.inSelectionMode,
                            selected = item.id in state.selection,
                            onClick = {
                                if (state.inSelectionMode) {
                                    onToggleSelection(item.id)
                                } else {
                                    onOpenItem(item.id)
                                }
                            },
                            onLongClick = { onBeginSelection(item.id) },
                            onToggleDone = {
                                // While picking rows a tap means "select", never "tick".
                                if (state.inSelectionMode) {
                                    onToggleSelection(item.id)
                                } else {
                                    onToggleDone(item.id)
                                }
                            },
                        )
                    }
                }
            }
        }

        // Creating a note is not a batch action: while rows are being picked, the FAB would only
        // be in the way of the delete button.
        if (!state.inSelectionMode) {
            FloatingActionButton(
                onClick = onNew,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 26.dp)
                    .size(58.dp)
                    .border(1.5.dp, Ink.copy(alpha = 0.16f), CircleShape),
                containerColor = Sun,
                contentColor = Ink,
            ) {
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_plus),
                    contentDescription = stringResource(R.string.action_new),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/**
 * Replaces the normal header while rows are being picked: a count, 全选/取消全选, and the two
 * batch actions. "Mark done" only appears when the selection actually contains todos.
 */
@Composable
private fun SelectionHeader(
    state: AppUiState,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSetSelectedDone: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CuteIconButton(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.action_cancel_selection),
            onClick = onClearSelection,
            tint = Ink,
        )
        Text(
            text = stringResource(R.string.selected_count, state.selection.size),
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            modifier = Modifier.padding(start = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(
                if (state.allVisibleSelected) R.string.action_select_none
                else R.string.action_select_all,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = Ink,
            modifier = Modifier
                .clip(CuteShapeSmall)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (state.allVisibleSelected) onClearSelection() else onSelectAll() },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (state.selectedTodoCount > 0) {
            CuteIconButton(
                painter = painterResource(
                    if (state.selectedTodosAllDone) R.drawable.ic_todo_open else R.drawable.ic_check,
                ),
                contentDescription = stringResource(
                    if (state.selectedTodosAllDone) R.string.action_mark_undone
                    else R.string.action_mark_done,
                ),
                onClick = { onSetSelectedDone(!state.selectedTodosAllDone) },
                tint = Ink,
            )
        }
        CuteIconButton(
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = stringResource(R.string.action_delete),
            onClick = onDeleteSelected,
            tint = Ink,
        )
    }
}

@Composable
private fun Header(
    state: AppUiState,
    onToggleSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (state.tab == Kind.NOTE) R.string.tab_notes else R.string.tab_todos,
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
            )
            Text(
                text = if (state.tab == Kind.NOTE) {
                    "${state.noteCount} 条笔记"
                } else {
                    "${state.todoCount} 条待办 · ${state.openTodoCount} 条未完成"
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        CuteIconButton(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = stringResource(R.string.action_search),
            onClick = onToggleSearch,
            tint = if (state.searchOpen) Ink else InkSoft,
        )
        CuteIconButton(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.action_settings),
            onClick = onOpenSettings,
            tint = InkSoft,
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Tapping the search icon already says "I want to type" — making the user then tap the field
    // as well is a wasted step, so the cursor and the IME come up together with the bar.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Row(
        modifier
            .clip(CuteShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, Ink.copy(alpha = 0.13f), CuteShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = InkSoft,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.hint_search),
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkSoft,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                cursorBrush = SolidColor(Ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
    }
}

@Composable
private fun ItemCard(
    item: Item,
    index: Int,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleDone: () -> Unit,
) {
    val background = when {
        item.done -> Done
        item.isTodo -> TODO_COLORS[index % TODO_COLORS.size]
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
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 0.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        if (item.isTodo) {
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
                text = TimeText.relative(item.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private val TODO_COLORS = listOf(Sky, Mint, Blush)
