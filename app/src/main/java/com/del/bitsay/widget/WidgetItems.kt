package com.del.bitsay.widget

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import com.del.bitsay.R
import com.del.bitsay.core.model.Item
import com.del.bitsay.core.util.TextPreview

/**
 * Turns domain [Item]s into the row [RemoteViews] the widget scrolls through.
 *
 * Rows are deliberately uniform: **one line of text, no timestamp, one fixed height**. A glance
 * at the home screen should answer "what is on my list", not "when did I touch it" — and a fixed
 * row height keeps scrolling predictable and makes the scrollbar a useful length indicator.
 *
 * Android 12's `RemoteViews.RemoteCollectionItems` is used instead of a `RemoteViewsService` +
 * `RemoteViewsFactory`: on Android 17 the service-based collection API is deprecated, and
 * building the rows here removes a binder round-trip and a manifest entry. The price is that all
 * rows are built eagerly, hence [MAX_ROWS].
 */
internal object WidgetItems {

    /**
     * A widget is glanced at, not browsed: nobody scrolls a 4-cell launcher card through 300
     * notes. Capping also keeps the RemoteViews binder transaction comfortably small.
     */
    const val MAX_ROWS = 50

    private val BACKGROUNDS = intArrayOf(
        R.drawable.widget_item_bg_1,
        R.drawable.widget_item_bg_2,
        R.drawable.widget_item_bg_3,
        R.drawable.widget_item_bg_4,
    )

    fun build(context: Context, items: List<Item>): List<Row> =
        items.take(MAX_ROWS).mapIndexed { index, item -> row(context, item, index) }

    private fun row(context: Context, item: Item, index: Int): Row {
        val views = RemoteViews(context.packageName, R.layout.widget_item)

        views.setTextViewText(R.id.widget_item_text, TextPreview.singleLine(item.text))
        views.setTextColor(
            R.id.widget_item_text,
            context.getColor(if (item.done) R.color.ink_soft else R.color.ink),
        )
        views.setInt(
            R.id.widget_item_text,
            "setPaintFlags",
            Paint.ANTI_ALIAS_FLAG or if (item.done) Paint.STRIKE_THRU_TEXT_FLAG else 0,
        )

        if (item.isTodo) {
            views.setViewVisibility(R.id.widget_item_icon, View.VISIBLE)
            views.setImageViewResource(
                R.id.widget_item_icon,
                if (item.done) R.drawable.ic_todo_done else R.drawable.ic_todo_open,
            )
            // Independent action: tick the todo without leaving the home screen.
            views.setOnClickFillInIntent(
                R.id.widget_item_icon,
                Intent().putExtra(WidgetContract.EXTRA_ITEM_ID, item.id)
                    .putExtra(WidgetContract.EXTRA_ACTION, WidgetContract.ITEM_ACTION_TOGGLE_DONE),
            )
        } else {
            views.setViewVisibility(R.id.widget_item_icon, View.GONE)
        }

        views.setInt(
            R.id.widget_item_root,
            "setBackgroundResource",
            if (item.done) R.drawable.widget_item_bg_5 else BACKGROUNDS[index % BACKGROUNDS.size],
        )
        views.setOnClickFillInIntent(
            R.id.widget_item_root,
            Intent().putExtra(WidgetContract.EXTRA_ITEM_ID, item.id)
                .putExtra(WidgetContract.EXTRA_ACTION, WidgetContract.ITEM_ACTION_OPEN),
        )
        return Row(id = item.id, views = views)
    }

    /** A row plus the stable id the launcher uses to keep scroll state. */
    data class Row(val id: Long, val views: RemoteViews)
}
