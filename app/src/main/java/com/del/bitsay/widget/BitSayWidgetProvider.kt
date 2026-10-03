package com.del.bitsay.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.del.bitsay.BitSayApp
import com.del.bitsay.core.model.Kind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The desktop widget.
 *
 * Supported interactions (all of them work without opening the app):
 *  * tap the 笔记 / 待办 tabs — switch this widget between the two lists;
 *  * tap `+` — open the editor on a blank note/todo of the kind on screen;
 *  * tap a row — open that note/todo;
 *  * tap the circle on a todo row — tick it off in place;
 *  * tap the ↗ button — open the app itself;
 *  * resize — the host reports new options and the rows re-render at the new density.
 */
class BitSayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetRenderer.render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetRenderer.render(context, manager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = WidgetPrefs(context)
        appWidgetIds.forEach(prefs::forget)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetContract.ACTION_SET_KIND -> {
                val widgetId = intent.getIntExtra(WidgetContract.EXTRA_WIDGET_ID, -1)
                val kind = Kind.ofCode(intent.getIntExtra(WidgetContract.EXTRA_KIND, Kind.NOTE.code))
                if (widgetId != -1) setKind(context, widgetId, kind)
                return
            }

            WidgetContract.ACTION_ITEM_CLICK -> {
                handleItemClick(context, intent)
                return
            }

            WidgetContract.ACTION_REFRESH -> {
                WidgetUpdater.refreshAll(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    private fun setKind(context: Context, widgetId: Int, kind: Kind) {
        val prefs = WidgetPrefs(context)
        if (prefs.kindOf(widgetId) == kind) return
        prefs.setKind(widgetId, kind)
        // The whole list is replaced, so start the other kind at its top.
        WidgetRenderer.render(context, AppWidgetManager.getInstance(context), widgetId, scrollToTop = true)
    }

    private fun handleItemClick(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(WidgetContract.EXTRA_ITEM_ID, -1L)
        if (itemId <= 0L) return

        when (intent.getStringExtra(WidgetContract.EXTRA_ACTION)) {
            WidgetContract.ITEM_ACTION_TOGGLE_DONE -> toggleDone(context, itemId)

            // Open the floating window rather than MainActivity: the app's own task must not be
            // dragged to the front just to show one entry.
            else -> context.startActivity(
                Intent(context, WidgetEntryActivity::class.java).apply {
                    action = WidgetContract.ACTION_OPEN_ITEM
                    putExtra(WidgetContract.EXTRA_ITEM_ID, itemId)
                    putExtra(WidgetContract.EXTRA_FROM_WIDGET, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    /**
     * `onReceive` runs on the main thread; `goAsync` lets the database write finish before the
     * broadcast is considered done, so the launcher always sees fresh data.
     */
    private fun toggleDone(context: Context, itemId: Long) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                BitSayApp.container(appContext).repository.toggleDone(itemId)
                WidgetUpdater.refreshAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Re-renders every live instance of the widget. Called whenever the database changes. */
object WidgetUpdater {

    /**
     * @param scrollToTop true only when a row was just inserted, so the widget reveals it instead
     *   of keeping the user's current scroll position (see [WidgetRenderer.render]).
     */
    fun refreshAll(context: Context, scrollToTop: Boolean = false) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(
            ComponentName(context.applicationContext, BitSayWidgetProvider::class.java),
        )
        if (ids == null || ids.isEmpty()) return
        ids.forEach { WidgetRenderer.render(context, manager, it, scrollToTop) }
    }
}
