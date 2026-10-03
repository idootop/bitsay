package com.del.bitsay.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * "Add this widget to my home screen" from inside the app.
 *
 * `requestPinAppWidget` shows the launcher's own confirmation sheet and — because the provider
 * declares `android:configure` — also runs [WidgetConfigActivity] so the user picks notes or
 * todos before the widget lands. Launchers are free to ignore the request, hence [isSupported]
 * and the boolean result: the settings screen falls back to the manual instructions.
 */
object WidgetPinner {

    fun isSupported(context: Context): Boolean =
        runCatching { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }
            .getOrDefault(false)

    /** @return true when the launcher accepted the request (the user still has to confirm). */
    fun request(context: Context): Boolean = runCatching {
        val manager = AppWidgetManager.getInstance(context)
        manager.isRequestPinAppWidgetSupported &&
            manager.requestPinAppWidget(
                ComponentName(context, BitSayWidgetProvider::class.java),
                null,
                null,
            )
    }.getOrDefault(false)
}
