package com.del.bitsay.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.del.bitsay.R
import com.del.bitsay.core.model.Kind
import com.del.bitsay.ui.AppUiState
import com.del.bitsay.ui.components.CuteIconButton
import com.del.bitsay.ui.components.EmptyHint
import com.del.bitsay.ui.components.ItemList
import com.del.bitsay.ui.components.SegmentedTabs
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.InkSoft
import com.del.bitsay.ui.theme.Mint
import com.del.bitsay.ui.theme.Sun

@Composable
fun ListScreen(
    state: AppUiState,
    onSelectTab: (Kind) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onToggleDone: (Long) -> Unit,
    onNew: () -> Unit,
    onBeginSelection: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read once here: the tab's accent lambda is a plain lambda, where a @Composable colour
    // accessor cannot be called.
    val sunAccent = Sun
    val mintAccent = Mint

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Selection mode replaces the whole header rather than stacking on top of it: while
            // picking rows, "search / settings / which tab" are all irrelevant.
            if (state.inSelectionMode) {
                SelectionHeader(
                    state = state,
                    onClearSelection = onClearSelection,
                    onDeleteSelected = onDeleteSelected,
                )
            } else {
                Header(
                    state = state,
                    onOpenSearch = onOpenSearch,
                    onOpenSettings = onOpenSettings,
                )
                SegmentedTabs(
                    options = listOf(Kind.NOTE, Kind.TODO),
                    selected = state.tab,
                    label = { stringResource(if (it == Kind.NOTE) R.string.tab_notes else R.string.tab_todos) },
                    accent = { if (it == Kind.NOTE) sunAccent else mintAccent },
                    onSelect = onSelectTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                )
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
                ItemList(
                    items = items,
                    selecting = state.inSelectionMode,
                    selection = state.selection,
                    onClick = { item ->
                        if (state.inSelectionMode) onToggleSelection(item.id) else onOpenItem(item.id)
                    },
                    onLongClick = { item -> onBeginSelection(item.id) },
                    // While picking rows a tap means "select", never "tick".
                    onToggleDone = { item ->
                        if (state.inSelectionMode) onToggleSelection(item.id) else onToggleDone(item.id)
                    },
                )
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
 * Replaces the normal header while rows are being picked: a count and the one batch action.
 * Deleting is the only thing worth doing to a mixed selection of notes and todos.
 */
@Composable
private fun SelectionHeader(
    state: AppUiState,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
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
    onOpenSearch: () -> Unit,
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
                    stringResource(R.string.count_notes, state.noteCount)
                } else {
                    stringResource(R.string.count_todos, state.todoCount, state.openTodoCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        CuteIconButton(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = stringResource(R.string.action_search),
            onClick = onOpenSearch,
            tint = InkSoft,
        )
        CuteIconButton(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.action_settings),
            onClick = onOpenSettings,
            tint = InkSoft,
        )
    }
}
