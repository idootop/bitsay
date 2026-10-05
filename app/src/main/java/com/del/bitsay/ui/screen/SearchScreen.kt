package com.del.bitsay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.del.bitsay.ui.components.SegmentedTabs
import com.del.bitsay.ui.theme.CardShape
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.InkFaint
import com.del.bitsay.ui.theme.InkSoft
import com.del.bitsay.ui.theme.Line
import com.del.bitsay.ui.theme.Paper

/**
 * Full-page search.
 *
 * It used to be a field that expanded inside the list header, which meant the results fought the
 * normal list for the same space and the query had no obvious way to be abandoned. A page of its
 * own gives search the whole screen, its own result set and one obvious way out.
 */
@Composable
fun SearchScreen(
    state: AppUiState,
    onQuery: (String) -> Unit,
    onSelectTab: (Kind) -> Unit,
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

    val results = state.searchResults

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp),
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
                focusRequester = focusRequester,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            )
        }

        SegmentedTabs(
            options = listOf(Kind.NOTE, Kind.TODO),
            selected = state.tab,
            label = { stringResource(if (it == Kind.NOTE) R.string.tab_notes else R.string.tab_todos) },
            onSelect = onSelectTab,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
        )

        when {
            state.query.isBlank() -> EmptyState(
                title = stringResource(R.string.empty_search_idle_title),
                hint = stringResource(R.string.empty_search_idle_hint),
            )
            results.isEmpty() -> EmptyState(
                title = stringResource(R.string.empty_search_title),
                hint = stringResource(R.string.empty_search_hint),
            )
            // Same rows as the home list, only without the room its FAB needs at the bottom.
            else -> ItemList(
                items = results,
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
