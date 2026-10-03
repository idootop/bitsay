package com.del.bitsay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.del.bitsay.R
import com.del.bitsay.core.model.Kind
import com.del.bitsay.core.util.TimeText
import com.del.bitsay.ui.AppUiState
import com.del.bitsay.ui.components.CuteIconButton
import com.del.bitsay.ui.theme.CuteShape
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.InkSoft
import com.del.bitsay.ui.theme.Mint
import com.del.bitsay.ui.theme.Paper
import com.del.bitsay.ui.theme.Sun

@Composable
fun EditorScreen(
    state: AppUiState,
    onDraftChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CuteIconButton(
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
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
            )
            Spacer(Modifier.weight(1f))
            if (state.editingId > 0L) {
                CuteIconButton(
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
                .clip(CuteShape)
                .background(Paper)
                .border(1.5.dp, Ink.copy(alpha = 0.13f), CuteShape)
                .padding(18.dp),
        ) {
            if (state.draft.isEmpty()) {
                Text(
                    text = stringResource(
                        if (state.editingKind == Kind.NOTE) R.string.hint_note else R.string.hint_todo,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkSoft.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                cursorBrush = SolidColor(Ink),
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SaveStatus(state)
                if (state.editingCreatedAt > 0L) {
                    Text(
                        text = "创建于 ${TimeText.absolute(state.editingCreatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            DoneButton(onClick = onSave)
        }
    }
}

/**
 * The editor writes to the database while you type, so the only feedback that matters is
 * "your words are safe" versus "still typing".
 */
@Composable
private fun SaveStatus(state: AppUiState) {
    val saved = state.editingUpdatedAt > 0L
    val (label, tint) = when {
        state.dirty -> stringResource(R.string.editor_saving) to InkSoft
        saved -> stringResource(R.string.editor_saved) to Ink
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
            text = if (saved && !state.dirty) "$label · ${TimeText.relative(state.editingUpdatedAt)}" else label,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}

@Composable
private fun DoneButton(onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CuteShape)
            .background(Mint)
            .border(1.5.dp, Ink.copy(alpha = 0.13f), CuteShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_done),
                style = MaterialTheme.typography.labelLarge,
                color = Ink,
            )
        }
    }
}
