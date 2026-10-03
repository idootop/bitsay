package com.del.bitsay.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.del.bitsay.R
import com.del.bitsay.core.backup.BackupError
import com.del.bitsay.core.util.TimeWording

/**
 * The relative-time wording for the current language. Built from resources here rather than inside
 * [com.del.bitsay.core.util.TimeText] so that the time rules stay pure and unit testable.
 */
fun Context.timeWording(): TimeWording = TimeWording(
    justNow = getString(R.string.time_just_now),
    minutesAgo = getString(R.string.time_minutes_ago),
    today = getString(R.string.time_today),
    yesterday = getString(R.string.time_yesterday),
    monthDayPattern = getString(R.string.time_pattern_month_day),
    yearMonthDayPattern = getString(R.string.time_pattern_year_month_day),
    fullPattern = getString(R.string.time_pattern_full),
)

@Composable
fun rememberTimeWording(): TimeWording {
    val context = LocalContext.current
    return remember(context) { context.timeWording() }
}

/** The user-facing sentence for a backup failure, in the current language. */
fun Context.backupErrorMessage(error: BackupError, schema: Int = 0): String = when (error) {
    BackupError.NOT_A_BACKUP -> getString(R.string.backup_error_not_a_backup)
    BackupError.EMPTY -> getString(R.string.backup_error_empty)
    BackupError.CORRUPT -> getString(R.string.backup_error_corrupt)
    BackupError.NEWER_SCHEMA -> getString(R.string.backup_error_newer, schema)
    BackupError.IO -> getString(R.string.backup_error_io)
}
