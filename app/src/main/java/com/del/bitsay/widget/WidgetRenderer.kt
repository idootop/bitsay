package com.del.bitsay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.del.bitsay.MainActivity
import com.del.bitsay.R
import com.del.bitsay.core.model.Kind

/**
 * Builds the [RemoteViews] for one widget instance. Stateless: everything it needs comes from
 * [WidgetPrefs] and the current host options, so any component (the provider, the config
 * activity, the app itself) can call it at any time.
 */
internal object WidgetRenderer {

    fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val kind = WidgetPrefs(context).kindOf(widgetId)
        val size = sizeOf(manager, widgetId)
        val items = context.appContainer.repository.snapshot(kind)

        val views = RemoteViews(context.packageName, R.layout.widget_bitsay)
        views.setTextViewText(R.id.widget_title, context.getString(kind.labelRes()))

        val empty = items.isEmpty()
        views.setViewVisibility(R.id.widget_list, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_empty, if (empty) View.VISIBLE else View.GONE)
        views.setViewVisibility(
            R.id.widget_header,
            if (size.showHeader) View.VISIBLE else View.GONE,
        )

        val rows = WidgetItems.build(context, items, size)
        views.setRemoteAdapter(
            R.id.widget_list,
            RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
                .apply { rows.forEach { addItem(it.id, it.views) } }
                .build(),
        )
        views.setPendingIntentTemplate(
            R.id.widget_list,
            // MUST be mutable: the launcher merges each row's fill-in intent into this one.
            PendingIntent.getBroadcast(
                context,
                requestCode(widgetId, RC_TEMPLATE),
                Intent(context, BitSayWidgetProvider::class.java).apply {
                    action = WidgetContract.ACTION_ITEM_CLICK
                    data = Uri.parse("bitsay://widget/$widgetId/item")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        )

        views.setOnClickPendingIntent(R.id.widget_add, quickAddIntent(context, widgetId, kind))
        views.setOnClickPendingIntent(R.id.widget_switch, toggleIntent(context, widgetId))
        views.setOnClickPendingIntent(R.id.widget_title, toggleIntent(context, widgetId))

        manager.updateAppWidget(widgetId, views)
    }

    fun sizeOf(manager: AppWidgetManager, widgetId: Int): WidgetSize {
        val options: Bundle = runCatching { manager.getAppWidgetOptions(widgetId) }
            .getOrDefault(Bundle.EMPTY)
        return WidgetSize.from(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
        )
    }

    /** `+` opens the app straight into a blank editor of the kind the widget is showing. */
    private fun quickAddIntent(context: Context, widgetId: Int, kind: Kind): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_NEW
            putExtra(WidgetContract.EXTRA_KIND, kind.code)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode(widgetId, RC_ADD),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun toggleIntent(context: Context, widgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(widgetId, RC_TOGGLE),
            Intent(context, BitSayWidgetProvider::class.java).apply {
                action = WidgetContract.ACTION_TOGGLE_KIND
                data = Uri.parse("bitsay://widget/$widgetId/toggle")
                putExtra(WidgetContract.EXTRA_WIDGET_ID, widgetId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun requestCode(widgetId: Int, slot: Int) = widgetId * 8 + slot

    private const val RC_TEMPLATE = 1
    private const val RC_ADD = 2
    private const val RC_TOGGLE = 3
}

internal fun Kind.labelRes(): Int =
    if (this == Kind.NOTE) R.string.tab_notes else R.string.tab_todos

internal val Context.appContainer get() = com.del.bitsay.BitSayApp.container(this)
