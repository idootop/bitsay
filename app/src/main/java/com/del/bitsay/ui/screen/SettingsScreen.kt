package com.del.bitsay.ui.screen

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import com.del.bitsay.R
import com.del.bitsay.i18n.AppLanguage
import com.del.bitsay.ui.theme.ThemeMode
import com.del.bitsay.ui.components.AppCard
import com.del.bitsay.ui.components.RoundIconButton
import com.del.bitsay.ui.components.SectionLabel
import com.del.bitsay.ui.components.SettingRow
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.InkFaint
import com.del.bitsay.ui.theme.PageTitleStyle
import com.del.bitsay.ui.theme.InkSoft

@Composable
fun SettingsScreen(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showLanguage by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }

    if (showTheme) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            current = themeMode,
            label = { stringResource(it.labelRes()) },
            onPick = { onThemeMode(it); showTheme = false },
            onDismiss = { showTheme = false },
        )
    }

    if (showLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = AppLanguage.entries,
            current = language,
            label = { stringResource(it.labelRes()) },
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
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                tint = Ink,
            )
            Text(
                text = stringResource(R.string.settings_title),
                style = PageTitleStyle,
                color = Ink,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // Order is deliberate: what this app IS, then how it looks, then the one thing you came
        // here to do with your data. The old page opened with statistics and a widget promo, which
        // made the top of the screen about the app rather than about the user.
        SectionLabel(stringResource(R.string.settings_about))
        AppCard(contentPadding = PaddingValues(17.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = Ink,
            )
            Text(
                text = stringResource(R.string.settings_version, version),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = InkFaint,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = stringResource(R.string.settings_about_tagline),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = InkSoft,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        SectionLabel(stringResource(R.string.settings_appearance))
        AppCard(contentPadding = PaddingValues(0.dp)) {
            SettingRow(
                painter = painterResource(R.drawable.ic_theme),
                title = stringResource(R.string.settings_theme),
                subtitle = stringResource(themeMode.labelRes()),
                onClick = { showTheme = true },
            )
            SettingRow(
                painter = painterResource(R.drawable.ic_language),
                title = stringResource(R.string.settings_language_display),
                subtitle = stringResource(language.labelRes()),
                onClick = { showLanguage = true },
            )
        }

        SectionLabel(stringResource(R.string.settings_backup))
        AppCard(contentPadding = PaddingValues(0.dp)) {
            SettingRow(
                painter = painterResource(R.drawable.ic_export),
                title = stringResource(R.string.settings_export),
                subtitle = stringResource(R.string.settings_export_desc),
                onClick = onExport,
            )
            SettingRow(
                painter = painterResource(R.drawable.ic_import),
                title = stringResource(R.string.settings_import),
                subtitle = stringResource(R.string.settings_import_desc),
                onClick = onImport,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

/** Three options, no free-form picker: each setting only ships a couple of values. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    current: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
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
                            text = label(option),
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

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
