package com.del.bitsay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp
import com.del.bitsay.R
import com.del.bitsay.core.model.Kind
import com.del.bitsay.core.util.TimeText
import com.del.bitsay.core.util.TimeWording
import com.del.bitsay.i18n.rememberTimeWording
import com.del.bitsay.ui.AppUiState
import com.del.bitsay.ui.components.RoundIconButton
import com.del.bitsay.ui.theme.CardShape
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.PageTitleStyle
import com.del.bitsay.ui.theme.Line
import com.del.bitsay.ui.theme.InkFaint
import com.del.bitsay.ui.theme.InkSoft
import com.del.bitsay.ui.theme.Paper

@Composable
fun EditorScreen(
    state: AppUiState,
    onDraftChange: (String) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wording = rememberTimeWording()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Opened to write something new → go straight into typing. Opening an existing entry is a
    // "look at it" gesture, so the keyboard stays away until the text is actually tapped.
    // Keyed on the session so a second "new entry" (widget `+` while the app sits behind it)
    // still focuses even though this composable was never disposed.
    LaunchedEffect(state.editorSession) {
        if (!state.autoFocusEditor) return@LaunchedEffect
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                tint = Ink,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (state.editingKind == Kind.NOTE) R.string.tab_notes else R.string.tab_todos,
                ),
                style = PageTitleStyle,
                color = Ink,
            )
            Spacer(Modifier.weight(1f))
            if (state.editingId > 0L) {
                RoundIconButton(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.action_delete),
                    onClick = onDelete,
                    tint = InkSoft,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(CardShape)
                .background(Paper)
                // The sheet is separated by a hairline, not a shadow: it is only one step brighter
                // than the floor, and a shadow would overstate how far it floats.
                .border(1.dp, Line, CardShape)
                .padding(22.dp),
        ) {
            if (state.draft.isEmpty()) {
                Text(
                    text = stringResource(
                        if (state.editingKind == Kind.NOTE) R.string.hint_note else R.string.hint_todo,
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                    color = InkFaint,
                )
            }
            BasicTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = Ink,
                    lineHeight = 26.sp, // writing gets more air than reading a list
                ),
                cursorBrush = SolidColor(Ink),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
            )
        }

        Spacer(Modifier.height(14.dp))

        // No save button on purpose: every keystroke is already in the database, and leaving the
        // screen (back, or the gesture) is the only action left. A button would only imply that
        // saving is something the user still has to remember to do.
        Column(Modifier.fillMaxWidth()) {
            SaveStatus(state, wording)
            if (state.editingCreatedAt > 0L) {
                Text(
                    text = stringResource(R.string.editor_created_at, TimeText.absolute(state.editingCreatedAt, wording)),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * The editor writes to the database while you type, so the only feedback that matters is
 * "your words are safe" versus "still typing".
 */
@Composable
private fun SaveStatus(state: AppUiState, wording: TimeWording) {
    val saved = state.editingUpdatedAt > 0L
    val (label, tint) = when {
        state.dirty -> stringResource(R.string.editor_saving) to InkSoft
        saved -> stringResource(R.string.editor_saved) to Ink
        state.quickCapture -> stringResource(R.string.editor_quick_capture_hint) to InkSoft
        else -> stringResource(R.string.editor_autosave_hint) to InkSoft
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (saved && !state.dirty) {
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = if (saved && !state.dirty) {
                stringResource(R.string.editor_saved_at, label, TimeText.relative(state.editingUpdatedAt, wording))
            } else {
                label
            },
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}

