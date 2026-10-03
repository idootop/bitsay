package com.del.bitsay.ui.screen

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.del.bitsay.R
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
    onBack: () -> Unit,
    onAddWidget: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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

        SectionTitle("概览")
        CuteCard(color = Sun) {
            Text(
                text = "$noteCount 条笔记 · $todoCount 条待办",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Text(
                text = "其中 $openTodoCount 条待办还没完成",
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

        SectionTitle("桌面小组件")
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

        SectionTitle(stringResource(R.string.settings_about))
        CuteCard(color = Lilac) {
            Text("比特记 Bitsay", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                text = "版本 $version",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "纯文字笔记 + 待办，零权限，数据只在你自己手里。",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}
