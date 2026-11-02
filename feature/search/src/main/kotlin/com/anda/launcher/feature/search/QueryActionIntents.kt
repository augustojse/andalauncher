package com.anda.launcher.feature.search

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.net.toUri
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Outgoing intents for [QueryAction]s. Each one opens another app (dialer, messages, mail,
 * browser, clock, calendar, contacts) with the query filled in; nothing is done silently.
 */
internal object QueryActionIntents {
    fun intent(action: QueryAction): Intent = when (action) {
        is QueryAction.Call -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.number, null))
        is QueryAction.Message -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", action.number, null))
        is QueryAction.Email -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", action.address, null))
        is QueryAction.OpenUrl -> Intent(Intent.ACTION_VIEW, action.url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        is QueryAction.SetAlarm -> Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
        is QueryAction.SetTimer -> Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        is QueryAction.AddEvent -> {
            val start = action.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = action.date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, action.title)
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        }
        is QueryAction.SaveContact -> Intent(ContactsContract.Intents.Insert.ACTION)
            .setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .apply {
                action.phone?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                action.email?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
            }
        is QueryAction.Custom -> custom(action)
    }

    fun title(action: QueryAction): String = when (action) {
        is QueryAction.Call -> "Call ${action.number}"
        is QueryAction.Message -> "Text ${action.number}"
        is QueryAction.Email -> "Email ${action.address}"
        is QueryAction.OpenUrl -> "Open ${action.url.removePrefix("https://").removePrefix("http://")}"
        is QueryAction.SetAlarm -> "Set an alarm for ${LocalTime.of(action.hour, action.minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}"
        is QueryAction.SetTimer -> "Start a ${QueryClassifier.formatDuration(action.seconds)} timer"
        is QueryAction.AddEvent -> if (action.title.isEmpty()) "New event" else "Add \"${action.title}\" to calendar"
        is QueryAction.SaveContact -> "Save as contact"
        is QueryAction.Custom -> if (action.custom.isAppSearch) "Search ${action.custom.label}" else action.custom.label
    }

    fun subtitle(action: QueryAction): String = when (action) {
        is QueryAction.Call -> "Phone"
        is QueryAction.Message -> "Messages"
        is QueryAction.Email -> "Email"
        is QueryAction.OpenUrl -> "Browser"
        is QueryAction.SetAlarm -> "Clock"
        is QueryAction.SetTimer -> "Clock"
        is QueryAction.AddEvent -> action.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        is QueryAction.SaveContact -> "Contacts"
        is QueryAction.Custom -> if (action.custom.isAppSearch) "Search in an app" else "Custom action"
    }

    fun icon(action: QueryAction): ImageVector = when (action) {
        is QueryAction.Call -> Icons.Filled.Call
        is QueryAction.Message -> Icons.AutoMirrored.Filled.Message
        is QueryAction.Email -> Icons.Filled.Email
        is QueryAction.OpenUrl -> Icons.Filled.OpenInBrowser
        is QueryAction.SetAlarm -> Icons.Filled.Alarm
        is QueryAction.SetTimer -> Icons.Filled.Timer
        is QueryAction.AddEvent -> Icons.Filled.Event
        is QueryAction.SaveContact -> Icons.Filled.PersonAdd
        is QueryAction.Custom -> if (action.custom.isAppSearch) Icons.Filled.Search else Icons.Filled.Bolt
    }

    /** An app's own search screen, or the user's intent with the query in its data and/or extra. */
    private fun custom(action: QueryAction.Custom): Intent {
        val c = action.custom
        val intentAction = c.intentAction
            ?: return Intent(Intent.ACTION_SEARCH).setPackage(c.packageName).putExtra(SearchManager.QUERY, action.query)
        return Intent(intentAction).apply {
            val data = c.dataFor(action.query)?.toUri()
            when {
                data != null && c.mimeType != null -> setDataAndType(data, c.mimeType)
                data != null -> setData(data)
                c.mimeType != null -> setType(c.mimeType)
            }
            c.extraKey?.let { putExtra(it, action.query) }
            c.packageName?.let { setPackage(it) }
        }
    }
}
