package com.del.bitsay.ui.screen

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.del.bitsay.R
import com.del.bitsay.core.model.Kind
import com.del.bitsay.ui.AppUiState
import com.del.bitsay.ui.components.ConfirmDialog
import com.del.bitsay.ui.components.EmptyState
import com.del.bitsay.ui.components.ItemList
import com.del.bitsay.ui.components.RoundIconButton
import com.del.bitsay.ui.components.SegmentedTabs
import com.del.bitsay.ui.theme.Accent
import com.del.bitsay.ui.theme.AccentInk
import com.del.bitsay.ui.theme.DisplayStyle
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.InkSoft

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
    var confirmDelete by remember { mutableStateOf(false) }

    // Notes and todos are two pages of one pager, so a horizontal swipe moves between them. Both
    // lists already live in the state, which is what makes this cheap: the page next to the current
    // one renders real content rather than a placeholder that swaps in after the state catches up.
    val tabs = remember { listOf(Kind.NOTE, Kind.TODO) }
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // Swipe -> tab. settledPage, not currentPage: reacting mid-drag would flip the tab, and with it
    // the header title, while the finger is still moving.
    //
    // rememberUpdatedState is load-bearing. This effect is keyed on pagerState, so it is launched
    // once and must not restart; a plain `state.tab` read inside it would be the value captured at
    // that first composition, forever. The symptom was a one-way swipe: swiping to todos worked
    // (TODO != stale NOTE), swiping back did not (NOTE == stale NOTE), leaving the header reading
    // "todos" above a list of notes.
    val currentTab by rememberUpdatedState(state.tab)
    val selectTab by rememberUpdatedState(onSelectTab)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val kind = tabs[page]
            if (kind != currentTab) selectTab(kind)
        }
    }
    // Tab -> swipe. Re-running after a swipe is a no-op because the page already matches, so the
    // two effects cannot chase each other.
    LaunchedEffect(state.tab) {
        val page = tabs.indexOf(state.tab)
        if (page >= 0 && pagerState.currentPage != page) pagerState.animateScrollToPage(page)
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_batch, state.selection.size),
            hint = stringResource(R.string.confirm_delete_hint),
            confirmLabel = stringResource(R.string.action_delete_confirm),
            onConfirm = { confirmDelete = false; onDeleteSelected() },
            onDismiss = { confirmDelete = false },
        )
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Selection mode replaces the whole header rather than stacking on top of it: while
            // picking rows, "search / settings / which tab" are all irrelevant.
            if (state.inSelectionMode) {
                SelectionHeader(
                    state = state,
                    onClearSelection = onClearSelection,
                    onDeleteSelected = { confirmDelete = true },
                )
            } else {
                Header(
                    state = state,
                    onOpenSearch = onOpenSearch,
                    onOpenSettings = onOpenSettings,
                )
                SegmentedTabs(
                    options = tabs,
                    selected = state.tab,
                    label = { stringResource(if (it == Kind.NOTE) R.string.tab_notes else R.string.tab_todos) },
                    onSelect = onSelectTab,
                    // Live scroll offset, so the indicator travels with the drag.
                    position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 2.dp),
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val kind = tabs[page]
                val items = if (kind == Kind.NOTE) state.notes else state.todos
                if (items.isEmpty()) {
                    val note = kind == Kind.NOTE
                    EmptyState(
                        title = stringResource(
                            if (note) R.string.empty_notes_title else R.string.empty_todos_title,
                        ),
                        hint = stringResource(
                            if (note) R.string.empty_notes_hint else R.string.empty_todos_hint,
                        ),
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
        }

        // Creating a note is not a batch action: while rows are being picked, the FAB would only
        // be in the way of the delete button.
        if (!state.inSelectionMode) {
            FloatingActionButton(
                onClick = onNew,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 26.dp)
                    .size(62.dp),
                shape = CircleShape,
                containerColor = Accent,
                contentColor = AccentInk,
                // Flat on purpose: a drop shadow under a solid black disc is noise, and it would
                // compete with the cards' own (absent) elevation.
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
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
            .padding(horizontal = 12.dp).padding(top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.action_cancel_selection),
            onClick = onClearSelection,
            tint = Ink,
        )
        Text(
            text = stringResource(R.string.selected_count, state.selection.size),
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        RoundIconButton(
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
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (state.tab == Kind.NOTE) R.string.tab_notes else R.string.tab_todos,
                ),
                style = DisplayStyle,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (state.tab == Kind.NOTE) {
                    stringResource(R.string.count_notes, state.noteCount)
                } else {
                    stringResource(R.string.count_todos, state.todoCount, state.openTodoCount)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = InkSoft,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // Sits at the title's cap height rather than centred on the two-line block: at 38sp the
        // icon buttons are much shorter than the text block, and centring drops them to the
        // baseline of the count line.
        // 8dp apart, not 0: two 40dp targets sharing an edge is a mis-tap waiting to happen.
        Row(
            Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundIconButton(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = stringResource(R.string.action_search),
                onClick = onOpenSearch,
            )
            RoundIconButton(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.action_settings),
                onClick = onOpenSettings,
            )
        }
    }
}
