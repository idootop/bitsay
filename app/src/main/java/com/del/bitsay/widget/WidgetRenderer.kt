package com.del.bitsay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.del.bitsay.MainActivity
import com.del.bitsay.R
import com.del.bitsay.core.model.Kind
import com.del.bitsay.i18n.withAppLanguage
import com.del.bitsay.ui.theme.ThemePrefs
import com.del.bitsay.ui.theme.paletteFor

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

        // The host inflates the layout with ITS configuration, i.e. the system language. Strings
        // declared in the XML would therefore ignore the in-app language setting, so everything
        // visible is assigned here from a context pinned to the chosen language.
        val ui = context.withAppLanguage()

        // The widget follows the APP's theme choice, not the launcher's night mode: the host
        // inflates the layout with its own configuration, so anything resource-driven would
        // ignore the setting entirely. Colours are therefore assigned explicitly here.
        val dark = ThemePrefs.isDark(context)
        val palette = paletteFor(dark)

        val views = RemoteViews(context.packageName, R.layout.widget_bitsay_v3)
        views.setInt(
            R.id.widget_root,
            "setBackgroundResource",
            if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg,
        )
        views.setTextViewText(R.id.widget_tab_notes, ui.getString(R.string.tab_notes))
        views.setTextViewText(R.id.widget_tab_todos, ui.getString(R.string.tab_todos))
        // Same copy the app's empty state uses, picked by which list this widget shows.
        val noteTab = kind == Kind.NOTE
        views.setTextViewText(
            R.id.widget_empty_title,
            ui.getString(if (noteTab) R.string.empty_notes_title else R.string.empty_todos_title),
        )
        // The app's hints mark the "+" with ** for bold. RemoteViews text is plain, so it is
        // stripped here rather than shipped as literal asterisks.
        views.setTextViewText(
            R.id.widget_empty_hint,
            ui.getString(if (noteTab) R.string.empty_notes_hint else R.string.empty_todos_hint)
                .replace("**", ""),
        )
        // Colours must be assigned here, not left to the layout's android:textColor.
        //
        // The layout is inflated by the LAUNCHER, with the launcher's configuration — so a
        // `@color/ink` in the XML resolves against the *system* night mode, not the app's theme
        // setting. With the phone in light mode and the app set to dark, that painted near-black
        // text on the widget's black tile. Every other text in this widget was already assigned
        // here; these two were added without it and were the only ones that broke.
        views.setTextColor(R.id.widget_empty_title, palette.ink.toArgb())
        views.setTextColor(R.id.widget_empty_hint, palette.inkSoft.toArgb())
        // The one green in the app, reserved for this plant.
        iconTint(views, R.id.widget_empty_sprout, palette.leaf)

        // The three icon buttons are static ink vectors, and a RemoteViews layout cannot follow the
        // app's theme choice through resources (the host inflates it with its own configuration).
        // Untinted they stayed #3D3A38 on the dark background #2C2A27 — a contrast ratio of 1.26:1,
        // i.e. effectively invisible. Tint them from the same palette as the rest of the widget.
        iconTint(views, R.id.widget_open_app, palette.ink)
        iconTint(views, R.id.widget_search, palette.ink)
        // The FAB glyph sits ON the accent, so it needs the accent's opposite — not `ink`. With
        // `ink` it was a near-black plus on a black disc in light mode: invisible.
        iconTint(views, R.id.widget_add, palette.accentInk)
        // ...and the disc itself has to come from the palette as well. Its drawable names the
        // static @color/accent (always black), so in dark mode a black disc would have carried a
        // near-black plus. Only the background resource follows the app theme; the XML cannot.
        views.setColorStateList(
            R.id.widget_add,
            "setBackgroundTintList",
            ColorStateList.valueOf(palette.accent.toArgb()),
        )

        // --- the two list tabs ---
        // The active tab is a plain canvas-coloured pill with ink text — not a colour. Both tabs
        // keep the same shape and only swap which one is lit, so "which list am I looking at" is
        // answered by contrast rather than by hue.
        // The lit tab is the same surface as a row, not a grey wash: the tile is the page colour
        // now, so a page-coloured pill would be invisible on it.
        tint(views, R.id.widget_tab_notes, if (kind == Kind.NOTE) palette.widgetRow else Color.Transparent)
        tint(views, R.id.widget_tab_todos, if (kind == Kind.TODO) palette.widgetRow else Color.Transparent)
        views.setTextColor(
            R.id.widget_tab_notes,
            (if (kind == Kind.NOTE) palette.ink else palette.inkFaint).toArgb(),
        )
        views.setTextColor(
            R.id.widget_tab_todos,
            (if (kind == Kind.TODO) palette.ink else palette.inkFaint).toArgb(),
        )
        views.setOnClickPendingIntent(R.id.widget_tab_notes, setKindIntent(context, widgetId, Kind.NOTE))
        views.setOnClickPendingIntent(R.id.widget_tab_todos, setKindIntent(context, widgetId, Kind.TODO))

        // --- list / empty state ---
        val empty = items.isEmpty()
        views.setViewVisibility(R.id.widget_list, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_empty, if (empty) View.VISIBLE else View.GONE)

        val rows = WidgetItems.build(context, items, palette)
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
        views.setOnClickPendingIntent(R.id.widget_search, searchIntent(context, widgetId))

        if (scrollToTop) {
            views.setInt(R.id.widget_list, "smoothScrollToPosition", 0)
        }

        manager.updateAppWidget(widgetId, views)
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

    /**
     * Search opens the same floating window as [`+`][quickAddIntent] and a row tap, landing on
     * the search page instead of the editor.
     *
     * It is deliberately *not* [MainActivity]: entering the app's task would leave the app's list
     * sitting behind the search page, so backing out would reveal a screen the user never asked
     * for. In its own task, back simply dismisses the window onto the home screen.
     */
    private fun searchIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, WidgetEntryActivity::class.java).apply {
            action = WidgetContract.ACTION_SEARCH
            putExtra(WidgetContract.EXTRA_FROM_WIDGET, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            requestCode(widgetId, RC_SEARCH),
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

    /** One white rounded shape, coloured per state — no second set of drawables for dark mode. */
    private fun tint(views: RemoteViews, viewId: Int, color: Color) {
        views.setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color.toArgb()))
    }

    /**
     * Tint an ImageView's drawable. [ImageView.setImageTintList] is `@RemotableViewMethod`
     * (android/widget/ImageView.java), which RemoteViews enforces at apply time, so this is the
     * supported way to recolour a vector in a widget. `setColorFilter` is NOT annotated and would
     * throw ActionException.
     */
    private fun iconTint(views: RemoteViews, viewId: Int, color: Color) {
        views.setColorStateList(viewId, "setImageTintList", ColorStateList.valueOf(color.toArgb()))
    }

    private fun requestCode(widgetId: Int, slot: Int) = widgetId * 8 + slot

    private const val RC_TEMPLATE = 1
    private const val RC_ADD = 2
    private const val RC_OPEN_APP = 3
    private const val RC_TAB_BASE = 4
    private const val RC_SEARCH = 7
}

internal fun Kind.labelRes(): Int =
    if (this == Kind.NOTE) R.string.tab_notes else R.string.tab_todos

internal val Context.appContainer get() = com.del.bitsay.BitSayApp.container(this)
