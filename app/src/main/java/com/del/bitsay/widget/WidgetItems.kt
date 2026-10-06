package com.del.bitsay.widget

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.del.bitsay.R
import com.del.bitsay.core.model.Item
import com.del.bitsay.core.util.TextPreview
import com.del.bitsay.ui.theme.BitSayPalette

/**
 * Turns domain [Item]s into the row [RemoteViews] the widget scrolls through.
 *
 * Rows are deliberately uniform: **one line of text, no timestamp, one fixed height**. A glance
 * at the home screen should answer "what is on my list", not "when did I touch it" — and a fixed
 * row height keeps scrolling predictable and makes the scrollbar a useful length indicator.
 *
 * Colours come from the same [BitSayPalette] the app uses, applied as a background *tint* on one
 * white shape (`setBackgroundTintList` is a `@RemotableViewMethod`). That is what lets the widget
 * follow the app's light/dark choice without shipping two sets of drawables — and it keeps the
 * widget's colours in step with the app's by construction.
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

    fun build(context: Context, items: List<Item>, palette: BitSayPalette): List<Row> =
        items.take(MAX_ROWS).map { item -> row(context, item, palette) }

    private fun row(context: Context, item: Item, palette: BitSayPalette): Row {
        val views = RemoteViews(context.packageName, R.layout.widget_item)

        views.setTextViewText(R.id.widget_item_text, TextPreview.singleLine(item.text))
        views.setTextColor(
            R.id.widget_item_text,
            if (item.done) palette.inkFaint.toArgb() else palette.ink.toArgb(),
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
            // Both states ship as one-colour line art precisely so this tint can exist: the
            // drawable's own colour would otherwise be frozen at whatever the XML says, and the
            // widget cannot follow the app's theme through resources.
            views.setColorStateList(
                R.id.widget_item_icon,
                "setImageTintList",
                ColorStateList.valueOf(palette.inkFaint.toArgb()),
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

        // The tick is a 22dp glyph centred inside a wider tap target, so it carries the left inset
        // itself (paddingStart on the ImageView). A note row has no tick, so the row supplies it.
        //
        // Both branches set it explicitly, and neither is cached: the launcher recycles these item
        // views, so a view that was a note row can be reused for a todo row. Leaving the todo
        // branch to inherit the XML default would then keep the note row's 14dp inset and push the
        // circle right again.
        val density = context.resources.displayMetrics.density
        val edge = (14 * density).toInt()
        views.setViewPadding(
            R.id.widget_item_root,
            if (item.isTodo) 0 else edge, 0, edge, 0,
        )

        // One row colour for everything, and it is the extreme: pure white in light mode, pure black
        // in dark. The rotating pastels were dropped long ago (on a home screen a rainbow list
        // competes with the wallpaper); what changed here is that the row no longer borrows the
        // page colour, which left it too close to the tile to read as a row.
        val background = if (item.done) palette.cardDone else palette.widgetRow
        views.setColorStateList(
            R.id.widget_item_root,
            "setBackgroundTintList",
            ColorStateList.valueOf(background.toArgb()),
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
