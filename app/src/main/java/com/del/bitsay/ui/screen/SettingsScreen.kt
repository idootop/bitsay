package com.del.bitsay.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.del.bitsay.R
import com.del.bitsay.i18n.AppLanguage
import com.del.bitsay.ui.components.CuteCard
import com.del.bitsay.ui.components.CuteIconButton
import com.del.bitsay.ui.components.SectionTitle
import com.del.bitsay.ui.components.SettingRow
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.Lilac
import com.del.bitsay.ui.theme.Sky
import com.del.bitsay.ui.theme.Sun

@Composable
fun SettingsScreen(
    noteCount: Int,
    todoCount: Int,
    openTodoCount: Int,
    canPinWidget: Boolean,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onAddWidget: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showLanguage by remember { mutableStateOf(false) }

    if (showLanguage) {
        LanguageDialog(
            current = language,
            onPick = { onLanguage(it); showLanguage = false },
            onDismiss = { showLanguage = false },
        )
    }
    val version = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("")
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
            )
        }

        SectionTitle(stringResource(R.string.settings_section_overview))
        CuteCard(color = Sun) {
            Text(
                text = stringResource(R.string.settings_stats, noteCount, todoCount),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Text(
                text = stringResource(R.string.count_open_todos, openTodoCount),
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionTitle(stringResource(R.string.settings_backup))
        CuteCard(contentPadding = PaddingValues(0.dp)) {
            SettingRow(
                painter = painterResource(R.drawable.ic_export),
                title = stringResource(R.string.settings_export),
                subtitle = stringResource(R.string.settings_export_desc),
                onClick = onExport,
            )
            Spacer(Modifier.height(1.dp))
            SettingRow(
                painter = painterResource(R.drawable.ic_import),
                title = stringResource(R.string.settings_import),
                subtitle = stringResource(R.string.settings_import_desc),
                onClick = onImport,
            )
        }

        SectionTitle(stringResource(R.string.settings_section_widget))
        CuteCard(color = Sky, contentPadding = PaddingValues(0.dp)) {
            if (canPinWidget) {
                // One tap inside the app beats making the user hunt through the launcher's
                // widget drawer. The launcher still shows its own confirm sheet.
                SettingRow(
                    painter = painterResource(R.drawable.ic_plus),
                    title = stringResource(R.string.settings_widget_add),
                    subtitle = stringResource(R.string.settings_widget_add_desc),
                    onClick = onAddWidget,
                )
            }
            Text(
                text = stringResource(R.string.settings_widget_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        SectionTitle(stringResource(R.string.settings_language))
        CuteCard(contentPadding = PaddingValues(0.dp)) {
            SettingRow(
                painter = painterResource(R.drawable.ic_language),
                title = stringResource(R.string.settings_language_display),
                subtitle = stringResource(language.labelRes()),
                onClick = { showLanguage = true },
            )
        }

        SectionTitle(stringResource(R.string.settings_about))
        CuteCard(color = Lilac) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                text = stringResource(R.string.settings_version, version),
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.settings_about_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

/** Three options, no free-form picker: the app only ships two languages plus "follow system". */
@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onPick: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                AppLanguage.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onPick(option) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == current, onClick = { onPick(option) })
                        Text(
                            text = stringResource(option.labelRes()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ink,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.CHINESE -> R.string.language_chinese
    AppLanguage.ENGLISH -> R.string.language_english
}
