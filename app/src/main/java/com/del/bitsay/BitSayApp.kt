package com.del.bitsay

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.del.bitsay.core.backup.BackupManager
import com.del.bitsay.core.db.SqliteItemStore
import com.del.bitsay.core.repo.ItemRepository
import com.del.bitsay.core.repo.ItemStore
import com.del.bitsay.i18n.AppLanguage
import com.del.bitsay.i18n.LanguagePrefs
import com.del.bitsay.ui.theme.ThemeMode
import com.del.bitsay.ui.theme.ThemePrefs
import com.del.bitsay.widget.WidgetPrefs
import com.del.bitsay.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Manual dependency container. A DI framework would be several hundred KB and an annotation
 * processor for exactly four objects — not worth it here.
 */
class AppContainer(context: Context) {

    /** Application context — safe to keep for the process lifetime. */
    val context: Context = context.applicationContext

    val store: ItemStore = SqliteItemStore(this.context)
    val repository = ItemRepository(store)
    val backup = BackupManager(repository, appVersion(this.context))
    val widgetPrefs = WidgetPrefs(this.context)
    val languagePrefs = LanguagePrefs(this.context)
    val themePrefs = ThemePrefs(this.context)

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
}

class BitSayApp : Application() {

    lateinit var container: AppContainer
        private set

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Warm the cache, then keep every widget in sync with the database. The widget layer
        // observes the domain; the domain never learns that widgets exist.
        appScope.launch {
            runCatching { container.repository.reload() }
            // The reload above is deliberately dropped by the `change` flow's `.drop(1)`, so nothing
            // else would push the widget on a cold start. It matters because "follow the system"
            // resolves at RENDER time: if the phone switched theme or language while this process
            // was dead, the widget is still showing the old one.
            WidgetUpdater.refreshAll(this@BitSayApp)
            var revealedUpTo = 0L
            container.repository.change
                .drop(1)
                .debounce(WIDGET_REFRESH_DEBOUNCE_MS)
                .collect { change ->
                    // A newest-first list keeps its scroll anchored to the previously-first row
                    // when rows are prepended, so a freshly created entry would sit hidden just
                    // above the viewport. Only an insert should yank the list back to the top;
                    // ticking a todo must leave the user exactly where they were.
                    val scrollToTop = change.insertedAt > revealedUpTo
                    revealedUpTo = change.insertedAt
                    WidgetUpdater.refreshAll(this@BitSayApp, scrollToTop)
                }
        }
    }

    /**
     * The system switched light/dark, language, or anything else that [Configuration] carries.
     *
     * `ThemePrefs.isDark` and `withAppLanguage` both resolve "follow the system" at render time, so
     * the widget only needs to be told to render again — no state to update. Nothing else does that:
     * the widget holds finished RemoteViews, and the launcher re-inflating its own layout does not
     * re-run our code.
     *
     * A fixed choice (light/dark, 中文/English) does not move with the system, so in that case this
     * is skipped rather than burning a render.
     *
     * LIMIT, stated plainly: this only fires while the app's process is alive. Android does not let
     * a manifest receiver listen for ACTION_CONFIGURATION_CHANGED ("you can not receive this through
     * components declared in manifests"), so a widget cannot be woken purely by a theme switch —
     * [onCreate] covers the next time the app is started instead.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val follows =
            container.themePrefs.current() == ThemeMode.SYSTEM ||
                container.languagePrefs.current() == AppLanguage.SYSTEM
        if (follows) WidgetUpdater.refreshAll(this)
    }

    companion object {
        private const val WIDGET_REFRESH_DEBOUNCE_MS = 120L

        /** Long-lived scope for work that must outlive any screen (widget refresh, DB warmup). */
        val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun container(context: Context): AppContainer =
            (context.applicationContext as BitSayApp).container
    }
}
