package com.del.bitsay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.del.bitsay.R
import com.del.bitsay.core.model.Kind
import com.del.bitsay.ui.AppUiState
import com.del.bitsay.ui.components.RoundIconButton
import com.del.bitsay.ui.components.EmptyState
import com.del.bitsay.ui.components.ItemList
import com.del.bitsay.ui.theme.PageHeaderPadding
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.InkSoft
import com.del.bitsay.ui.theme.Line
import com.del.bitsay.ui.theme.Paper

/**
 * Full-page search — of exactly one kind.
 *
 * It used to be a field that expanded inside the list header, which meant the results fought the
 * normal list for the same space and the query had no obvious way to be abandoned. A page of its
 * own gives search the whole screen, its own result set and one obvious way out.
 *
 * It also used to carry the same notes / todos pager as the home list. That is gone: search is
 * **single-kind**, decided by wherever you came from, and the only thing that says so is the
 * placeholder. A second segmented control on a page you reached by tapping a magnifier reads as a
 * second navigation bar, and a swipe that silently changes *what is being searched* is a worse
 * surprise than a swipe that does nothing.
 */
@Composable
fun SearchScreen(
    state: AppUiState,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onToggleDone: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // The page exists to type into, so the cursor and the IME come up with it.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val kind = state.searchKind

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                // end = 20 to match the list's own side padding, so the field's right edge lines up
                // with the cards under it (it was 14, i.e. 6dp proud of them).
                // bottom = 14 is the gap down to the first result. The tabs used to hold that gap
                // on their way out; deleting them took it with them, and the first card ended up
                // flush against the field — measured, both edges at y=93.4dp.
                .padding(
                    start = 14.dp,
                    end = 20.dp,
                    top = PageHeaderPadding,
                    bottom = PageHeaderPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                tint = Ink,
            )
            SearchField(
                value = state.query,
                onValueChange = onQuery,
                // The field is the page's only title, so it carries the one thing that would
                // otherwise be invisible: which list is being searched.
                placeholder = stringResource(
                    if (kind == Kind.NOTE) R.string.search_prompt_notes else R.string.search_prompt_todos,
                ),
                focusRequester = focusRequester,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            )
        }

        // No tabs under the field. The gap they used to leave now belongs to the results.
        when {
            state.query.isBlank() -> EmptyState(
                title = stringResource(R.string.empty_search_idle_title),
            )
            state.searchResults.isEmpty() -> EmptyState(
                title = stringResource(R.string.empty_search_title),
                hint = stringResource(R.string.empty_search_hint),
            )
            // Same rows as the home list, only without the room its FAB needs at the bottom.
            else -> ItemList(
                items = state.searchResults,
                onClick = { onOpenItem(it.id) },
                onToggleDone = { onToggleDone(it.id) },
                bottomPadding = 24.dp,
            )
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Paper)
            .border(1.dp, Line, CircleShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No magnifier inside the field. The screen is already titled by the field itself, and the
        // icon only pushed the placeholder off the left edge — the one thing you actually read.
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
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
