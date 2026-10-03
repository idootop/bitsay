package com.del.bitsay

import android.app.Application
import android.content.Context
import com.del.bitsay.core.backup.BackupManager
import com.del.bitsay.core.db.SqliteItemStore
import com.del.bitsay.core.repo.ItemRepository
import com.del.bitsay.core.repo.ItemStore
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

    companion object {
        private const val WIDGET_REFRESH_DEBOUNCE_MS = 120L

        /** Long-lived scope for work that must outlive any screen (widget refresh, DB warmup). */
        val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun container(context: Context): AppContainer =
            (context.applicationContext as BitSayApp).container
    }
}
