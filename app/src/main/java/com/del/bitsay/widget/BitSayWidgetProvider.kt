package com.del.bitsay.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.del.bitsay.BitSayApp
import com.del.bitsay.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The desktop widget.
 *
 * Supported interactions (all of them work without opening the app):
 *  * tap the title / ⇄ button — switch this widget between notes and todos;
 *  * tap `+` — open the editor on a blank note/todo of the kind on screen;
 *  * tap a row — open that note/todo;
 *  * tap the circle on a todo row — tick it off in place;
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
            WidgetContract.ACTION_TOGGLE_KIND -> {
                val widgetId = intent.getIntExtra(WidgetContract.EXTRA_WIDGET_ID, -1)
                if (widgetId != -1) toggleKind(context, widgetId)
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

    private fun toggleKind(context: Context, widgetId: Int) {
        val prefs = WidgetPrefs(context)
        prefs.setKind(widgetId, prefs.kindOf(widgetId).other)
        WidgetRenderer.render(context, AppWidgetManager.getInstance(context), widgetId)
    }

    private fun handleItemClick(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(WidgetContract.EXTRA_ITEM_ID, -1L)
        if (itemId <= 0L) return

        when (intent.getStringExtra(WidgetContract.EXTRA_ACTION)) {
            WidgetContract.ITEM_ACTION_TOGGLE_DONE -> toggleDone(context, itemId)

            else -> context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN
                    putExtra(WidgetContract.EXTRA_ITEM_ID, itemId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(
            ComponentName(context.applicationContext, BitSayWidgetProvider::class.java),
        )
        if (ids == null || ids.isEmpty()) return
        ids.forEach { WidgetRenderer.render(context, manager, it) }
    }
}
