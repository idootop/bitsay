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

/**
 * The only thing about the widget that reacts to being resized.
 *
 * Rows are always a fixed single line, so height never changes how an item looks — it only
 * decides whether the header earns its keep. The rule is expressed in **content terms** rather
 * than in cells or in a magic dp number: show the tabs only if the list still gets
 * [MIN_ROWS_WITH_HEADER] rows underneath them. At the 2x2 minimum that is false, so a small
 * widget trades its header for a usable list; from three rows up the header comes back.
 *
 * The launcher reports new options through `onAppWidgetOptionsChanged` on every resize.
 */
internal object WidgetLayout {

    const val MIN_ROWS_WITH_HEADER = 3

    fun showHeader(minHeightDp: Int, headerHeightDp: Int, rowHeightDp: Int): Boolean =
        minHeightDp >= headerHeightDp + MIN_ROWS_WITH_HEADER * rowHeightDp
}
