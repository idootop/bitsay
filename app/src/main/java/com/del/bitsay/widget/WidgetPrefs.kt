package com.del.bitsay.widget

import android.content.Context
import com.del.bitsay.core.model.Kind

/** Shared extras / actions between the provider, the renderer and the config activity. */
internal object WidgetContract {
    const val EXTRA_WIDGET_ID = "widget_id"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ITEM_ID = "item_id"
    const val EXTRA_ACTION = "action"

    /** Set when the app was opened from the home screen, so it can get out of the way again. */
    const val EXTRA_FROM_WIDGET = "from_widget"

    /** Tapping a tab asks for that list by name — no toggling, so the tap is never ambiguous. */
    const val ACTION_SET_KIND = "com.del.bitsay.action.WIDGET_SET_KIND"
    const val ACTION_ITEM_CLICK = "com.del.bitsay.action.WIDGET_ITEM_CLICK"
    const val ACTION_REFRESH = "com.del.bitsay.action.WIDGET_REFRESH"

    const val ITEM_ACTION_OPEN = "open"
    const val ITEM_ACTION_TOGGLE_DONE = "toggle_done"

    /** What the floating home-screen window ([WidgetEntryActivity]) is asked to do. */
    const val ACTION_NEW_ITEM = "com.del.bitsay.action.NEW_ITEM"
    const val ACTION_OPEN_ITEM = "com.del.bitsay.action.OPEN_ITEM"

    /** Sent by the widget's app button: bring the app up on its list, not wherever it was left. */
    const val ACTION_SHOW_LIST = "com.del.bitsay.action.SHOW_LIST"

    /** Sent by the widget's search button: open the app straight on its search page. */
    const val ACTION_SEARCH = "com.del.bitsay.action.SEARCH"
}

/** Per-widget configuration, persisted in its own SharedPreferences file. */
class WidgetPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun kindOf(widgetId: Int): Kind = Kind.ofCode(prefs.getInt(key(widgetId), Kind.NOTE.code))

    fun setKind(widgetId: Int, kind: Kind) {
        prefs.edit().putInt(key(widgetId), kind.code).apply()
    }

    fun forget(widgetId: Int) {
        prefs.edit().remove(key(widgetId)).apply()
    }

    private fun key(widgetId: Int) = "kind_$widgetId"

    companion object {
        const val FILE = "bitsay_widgets"
    }
}
