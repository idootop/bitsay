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
import com.del.bitsay.ui.components.CuteIconButton
import com.del.bitsay.ui.components.EmptyHint
import com.del.bitsay.ui.components.ItemList
import com.del.bitsay.ui.components.SegmentedTabs
import com.del.bitsay.ui.theme.CuteShape
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.InkSoft
import com.del.bitsay.ui.theme.Mint
import com.del.bitsay.ui.theme.Paper
import com.del.bitsay.ui.theme.Sun

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

    val sunAccent = Sun
    val mintAccent = Mint
    val results = state.searchResults

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CuteIconButton(
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

        Spacer(Modifier.padding(top = 12.dp))

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

        when {
            state.query.isBlank() -> EmptyHint(stringResource(R.string.search_prompt))
            results.isEmpty() -> EmptyHint(stringResource(R.string.empty_search))
            // Same rows as the home list, only without the room its FAB needs at the bottom.
            else -> ItemList(
                items = results,
                onClick = { onOpenItem(it.id) },
                onToggleDone = { onToggleDone(it.id) },
                bottomPadding = 32.dp,
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
            .clip(CuteShape)
            .background(Paper)
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
