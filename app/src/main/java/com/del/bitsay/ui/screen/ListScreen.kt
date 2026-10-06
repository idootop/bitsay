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
import com.del.bitsay.ui.theme.SPROUT_MAX_ROWS
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.InkSoft
import com.del.bitsay.ui.theme.PageHeaderPadding

/**
 * The tab trough at full size: a 40dp item inside [SegmentedTabs]' 4dp of padding on each side.
 * Kept in step by hand — it is the number this header's padding is derived from.
 */
private val TabTroughHeight = 48.dp

/** A page header's height: one [RoundIconButton]. */
private val PageHeaderHeight = 40.dp

/**
 * Vertical padding of the one-line header on a short window.
 *
 * Deliberately **not** [PageHeaderPadding]. The tabs keep their full size here (a 48dp trough),
 * while every other page header is 40dp — a single icon button — and on a wide window the two rows
 * sit side by side with their contents on one line. A row that is 8dp taller needs 4dp less padding
 * on each side to land on the same centre: with the page's own 14dp the left header sat 4dp low,
 * with the 8dp it had before it sat 6dp high. Both were measured.
 *
 * The alternative — shrinking the tabs to make the row 40dp — was tried and rejected: it lines the
 * boxes up but the control itself is then a different size in landscape than in portrait.
 */
private val CompactHeaderPadding = PageHeaderPadding - (TabTroughHeight - PageHeaderHeight) / 2

/**
 * Top padding of the **full** header — the one with the 38sp title, the count line and the tabs.
 *
 * 2dp less than [PageHeaderPadding], and that 2dp is a font metric, not taste: the title is 38sp
 * inside a 42sp line box, and a CJK glyph that size does not sit optically centred in its box — its
 * ink lands about 2dp low. Every other page title is centred in its 48dp header row instead, so
 * with the same padding the home title sat 2dp below the title of whatever page was open beside it.
 *
 * Nobody could see this until the two panes were side by side. Measured with both on screen
 * (1600×720dp window): right-hand title 70.6dp, home title 72.6dp → 70.6dp after the nudge.
 */
private val HomeHeaderPadding = PageHeaderPadding - 2.dp

@Composable
fun ListScreen(
    state: AppUiState,
    /** See [ItemList.entering]. Owned by the caller so it survives this screen being torn down. */
    seenItemIds: MutableMap<Kind, MutableSet<Long>>,
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
    /** Fold the header onto one line. See [com.del.bitsay.ui.theme.CompactHeaderHeight]. */
    compactTop: Boolean = false,
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
            } else if (compactTop) {
                // One row: tabs where the title was, search and settings on the right. The title
                // and the count line are both gone — see CompactHeaderHeight for why.
                Row(
                    Modifier
                        .fillMaxWidth()
                        // 12 on both sides, not 20/12: the icon button carries ~9dp of its own
                        // padding, so 12 puts the *glyph* at the page's 20dp margin either way —
                        // and equal sides are what put the tabs exactly on the centre line.
                        //
                        .padding(horizontal = 12.dp, vertical = CompactHeaderPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The two buttons split to opposite ends with the tabs between them. Kept
                    // together on the right the row reads lopsided, and the tabs get shoved into
                    // the corner against them.
                    SettingsButton(onClick = onOpenSettings)
                    SegmentedTabs(
                        options = tabs,
                        selected = state.tab,
                        label = { stringResource(if (it == Kind.NOTE) R.string.tab_notes else R.string.tab_todos) },
                        onSelect = onSelectTab,
                        position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                        // **Full size**, not shrunk. The 40dp item is exactly the height of the
                        // 48dp icon button beside it, so the row's height is the same as every
                        // other header in the app — shrink the tabs and the left pane's header
                        // stops lining up with the right pane's.
                        modifier = Modifier.weight(1f),
                    )
                    SearchButton(onClick = onOpenSearch)
                }
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
                        // The gap between the tabs and the first row lives here, on the element
                        // above the list, rather than inside the list as head padding.
                        .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 14.dp),
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val kind = tabs[page]
                val items = if (kind == Kind.NOTE) state.notes else state.todos

                // Which rows are new since the last pass. Keyed on `items`, so it recomputes when
                // the list changes rather than on every recomposition — a tick or a delete leaves
                // the id set identical and therefore animates nothing.
                // Rebuilt only when the list itself changes. Rows consume their own entries as
                // they are composed (see ItemList), so what is left here is exactly "still to be
                // shown for the first time".
                val entering = remember(items) {
                    val seen = seenItemIds.getOrPut(kind) { mutableSetOf() }
                    var order = 0
                    // In list order, and only up to SPROUT_MAX_ROWS: see that constant for why a
                    // row further down must not be armed. Building the map in list order also means
                    // the stagger follows what the eye sees, top to bottom.
                    val batch = LinkedHashMap<Long, Int>()
                    for (id in items.asSequence().map { it.id }) {
                        if (order >= SPROUT_MAX_ROWS) break
                        if (id !in seen) batch[id] = order++
                    }
                    seen.clear()
                    seen.addAll(items.map { it.id })
                    batch
                }

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
                        entering = entering,
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
            .padding(start = 20.dp, end = 12.dp, top = HomeHeaderPadding, bottom = 12.dp),
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
        // baseline of the count line. (In the one-line header this offset is simply dropped.)
        Row(Modifier.padding(top = 2.dp)) {
            TopBarActions(onOpenSearch = onOpenSearch, onOpenSettings = onOpenSettings)
        }
    }
}

/**
 * Search and settings, as a pair.
 *
 * Both header heights need the same two buttons at the same distance apart — 8dp, not 0: two 40dp
 * targets sharing an edge is a mis-tap waiting to happen. The one-line header takes them
 * individually instead, on opposite sides of the tabs.
 */
@Composable
private fun TopBarActions(onOpenSearch: () -> Unit, onOpenSettings: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchButton(onClick = onOpenSearch)
        SettingsButton(onClick = onOpenSettings)
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    RoundIconButton(
        painter = painterResource(R.drawable.ic_search),
        contentDescription = stringResource(R.string.action_search),
        onClick = onClick,
    )
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    RoundIconButton(
        painter = painterResource(R.drawable.ic_settings),
        contentDescription = stringResource(R.string.action_settings),
        onClick = onClick,
    )
}
