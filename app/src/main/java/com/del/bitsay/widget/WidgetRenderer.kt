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

    /**
     * @param scrollToTop bring row 0 back into view. Required after an insert: a list sorted
     *   newest-first re-anchors its scroll to the previously-first row whenever its data changes
     *   (`AbsListView.rememberSyncState` → `SYNC_FIRST_POSITION`), so a row prepended at index 0
     *   would stay hidden above the viewport. `ListView.setSelection` is *not* annotated
     *   `@RemotableViewMethod` and is rejected by RemoteViews; `smoothScrollToPosition` is the
     *   annotated equivalent and is what we can legally call from here.
     */
    fun render(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        scrollToTop: Boolean = false,
    ) {
        val kind = WidgetPrefs(context).kindOf(widgetId)
        val items = context.appContainer.repository.snapshot(kind)

        val views = RemoteViews(context.packageName, R.layout.widget_bitsay)

        // --- the two list tabs ---
        views.setInt(
            R.id.widget_tab_notes,
            "setBackgroundResource",
            if (kind == Kind.NOTE) R.drawable.widget_tab_notes_on else R.drawable.widget_tab_off,
        )
        views.setInt(
            R.id.widget_tab_todos,
            "setBackgroundResource",
            if (kind == Kind.TODO) R.drawable.widget_tab_todos_on else R.drawable.widget_tab_off,
        )
        views.setTextColor(
            R.id.widget_tab_notes,
            context.getColor(if (kind == Kind.NOTE) R.color.ink else R.color.ink_soft),
        )
        views.setTextColor(
            R.id.widget_tab_todos,
            context.getColor(if (kind == Kind.TODO) R.color.ink else R.color.ink_soft),
        )
        views.setOnClickPendingIntent(R.id.widget_tab_notes, setKindIntent(context, widgetId, Kind.NOTE))
        views.setOnClickPendingIntent(R.id.widget_tab_todos, setKindIntent(context, widgetId, Kind.TODO))

        // --- list / empty state ---
        val empty = items.isEmpty()
        views.setViewVisibility(R.id.widget_list, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_empty, if (empty) View.VISIBLE else View.GONE)
        views.setViewVisibility(
            R.id.widget_header,
            if (showHeader(context, manager, widgetId)) View.VISIBLE else View.GONE,
        )

        val rows = WidgetItems.build(context, items)
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

        // --- buttons ---
        views.setOnClickPendingIntent(R.id.widget_add, quickAddIntent(context, widgetId, kind))
        views.setOnClickPendingIntent(R.id.widget_open_app, openAppIntent(context, widgetId))

        if (scrollToTop) {
            views.setInt(R.id.widget_list, "smoothScrollToPosition", 0)
        }

        manager.updateAppWidget(widgetId, views)
    }

    /**
     * The header is dropped on widgets too short to show it *and* a usable list. The thresholds
     * come from the actual dimens rather than hard-coded numbers, so changing a row height in
     * `dimens.xml` automatically moves the cutoff.
     */
    fun showHeader(context: Context, manager: AppWidgetManager, widgetId: Int): Boolean {
        val options: Bundle = runCatching { manager.getAppWidgetOptions(widgetId) }
            .getOrDefault(Bundle.EMPTY)
        val res = context.resources
        val density = res.displayMetrics.density
        return WidgetLayout.showHeader(
            minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
            headerHeightDp = (res.getDimension(R.dimen.widget_header_height) / density).toInt(),
            rowHeightDp = (res.getDimension(R.dimen.widget_row_height) / density).toInt(),
        )
    }

    /**
     * `+` opens the floating home-screen window on a blank editor of the kind on screen. It is
     * deliberately *not* [MainActivity]: that would drag the app's whole task forward and flash
     * its list before the editor appeared.
     */
    private fun quickAddIntent(context: Context, widgetId: Int, kind: Kind): PendingIntent {
        val intent = Intent(context, WidgetEntryActivity::class.java).apply {
            action = WidgetContract.ACTION_NEW_ITEM
            putExtra(WidgetContract.EXTRA_KIND, kind.code)
            putExtra(WidgetContract.EXTRA_FROM_WIDGET, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            requestCode(widgetId, RC_ADD),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The app button: bring up the app's own list, wherever the app was left last time. */
    private fun openAppIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = WidgetContract.ACTION_SHOW_LIST
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode(widgetId, RC_OPEN_APP),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setKindIntent(context: Context, widgetId: Int, kind: Kind): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(widgetId, RC_TAB_BASE + kind.code),
            Intent(context, BitSayWidgetProvider::class.java).apply {
                action = WidgetContract.ACTION_SET_KIND
                data = Uri.parse("bitsay://widget/$widgetId/tab/${kind.code}")
                putExtra(WidgetContract.EXTRA_WIDGET_ID, widgetId)
                putExtra(WidgetContract.EXTRA_KIND, kind.code)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun requestCode(widgetId: Int, slot: Int) = widgetId * 8 + slot

    private const val RC_TEMPLATE = 1
    private const val RC_ADD = 2
    private const val RC_OPEN_APP = 3
    private const val RC_TAB_BASE = 4
}

internal fun Kind.labelRes(): Int =
    if (this == Kind.NOTE) R.string.tab_notes else R.string.tab_todos

internal val Context.appContainer get() = com.del.bitsay.BitSayApp.container(this)
