package com.del.bitsay.widget

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.del.bitsay.R
import com.del.bitsay.core.model.Kind
import com.del.bitsay.i18n.withAppLanguage
import com.del.bitsay.ui.components.AppCard
import com.del.bitsay.ui.components.PaperBackground
import com.del.bitsay.ui.theme.BitSayTheme
import com.del.bitsay.ui.theme.CardShape
import com.del.bitsay.ui.theme.AccentInk
import com.del.bitsay.ui.theme.Card
import com.del.bitsay.ui.theme.Ink
import com.del.bitsay.ui.theme.Line
import com.del.bitsay.ui.theme.AccentSoft
import com.del.bitsay.ui.theme.Accent
import com.del.bitsay.ui.theme.InkSoft

/**
 * Shown by the launcher when the widget is dropped on the home screen (and again later,
 * because `widgetFeatures="reconfigurable"`): pick which list this instance shows.
 */
class WidgetConfigActivity : ComponentActivity() {

    /** Same language pinning as the other activities; this screen shows strings too. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLanguage())
    }


    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val prefs = WidgetPrefs(this)
        setContent {
            BitSayTheme {
                var kind by remember { mutableStateOf(prefs.kindOf(widgetId)) }
                ConfigScreen(
                    kind = kind,
                    onPick = { kind = it },
                    onConfirm = {
                        prefs.setKind(widgetId, kind)
                        WidgetRenderer.render(this, AppWidgetManager.getInstance(this), widgetId)
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                        )
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun ConfigScreen(
    kind: Kind,
    onPick: (Kind) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    PaperBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(R.string.widget_config_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
            )
            Spacer(Modifier.height(18.dp))

            OptionCard(
                title = stringResource(R.string.widget_config_notes),
                subtitle = stringResource(R.string.widget_config_notes_desc),
                selected = kind == Kind.NOTE,
                onClick = { onPick(Kind.NOTE) },
            )
            Spacer(Modifier.height(12.dp))
            OptionCard(
                title = stringResource(R.string.widget_config_todos),
                subtitle = stringResource(R.string.widget_config_todos_desc),
                selected = kind == Kind.TODO,
                onClick = { onPick(Kind.TODO) },
            )

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(CardShape)
                        .border(1.dp, Line, CardShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCancel,
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.cancel), color = InkSoft)
                }
                Box(
                    Modifier
                        .weight(1.4f)
                        .clip(CardShape)
                        .background(Accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onConfirm,
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.widget_config_confirm),
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentInk,
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Selection is shown the same way as the segmented tabs: a solid accent fill with inverted
    // text. Reusing the one accent here keeps "chosen" meaning a single thing app-wide.
    val onAccent = selected
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) Accent else Card,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (onAccent) AccentInk else Ink,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (onAccent) AccentInk.copy(alpha = 0.72f) else InkSoft,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (selected) {
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = AccentInk,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
