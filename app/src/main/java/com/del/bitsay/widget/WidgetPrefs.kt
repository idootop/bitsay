package com.del.bitsay.widget

import android.content.Context
import com.del.bitsay.core.model.Kind

/** Shared extras / actions between the provider, the renderer and the config activity. */
internal object WidgetContract {
    const val EXTRA_WIDGET_ID = "widget_id"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ITEM_ID = "item_id"
    const val EXTRA_ACTION = "action"

    const val ACTION_TOGGLE_KIND = "com.del.bitsay.action.WIDGET_TOGGLE_KIND"
    const val ACTION_ITEM_CLICK = "com.del.bitsay.action.WIDGET_ITEM_CLICK"
    const val ACTION_REFRESH = "com.del.bitsay.action.WIDGET_REFRESH"

    const val ITEM_ACTION_OPEN = "open"
    const val ITEM_ACTION_TOGGLE_DONE = "toggle_done"
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
 * How much room the host gave us. The launcher calls `onAppWidgetOptionsChanged` on every
 * resize, so the widget gets denser or roomier as the user drags its handles.
 */
internal enum class WidgetSize(val maxLines: Int, val showTime: Boolean, val showHeader: Boolean) {
    /** One cell tall: a single line per row, header hidden to buy back space. */
    COMPACT(maxLines = 1, showTime = false, showHeader = false),

    /** The default 3x2 widget. */
    REGULAR(maxLines = 2, showTime = false, showHeader = true),

    /** 4+ cells tall: three lines per row plus a timestamp. */
    EXPANDED(maxLines = 3, showTime = true, showHeader = true),
    ;

    companion object {
        fun from(minWidthDp: Int, minHeightDp: Int): WidgetSize = when {
            minHeightDp in 1..COMPACT_MAX_HEIGHT || minWidthDp in 1..COMPACT_MAX_WIDTH -> COMPACT
            minHeightDp >= EXPANDED_MIN_HEIGHT -> EXPANDED
            else -> REGULAR
        }

        private const val COMPACT_MAX_HEIGHT = 90
        private const val COMPACT_MAX_WIDTH = 150
        private const val EXPANDED_MIN_HEIGHT = 200
    }
}
