package com.anda.launcher.feature.home

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.text.format.DateFormat
import com.anda.launcher.core.model.GlanceSettings
import com.anda.launcher.core.ui.theme.LocalWallpaperTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class NextEvent(val title: String, val begin: Long, val id: Long)

/**
 * Our own "At a Glance" (Smartspace data is private to Pixel Launcher, see docs/research/001):
 * date, next alarm, charging state and, opt-in, the next calendar event.
 */
@Composable
internal fun GlanceHeader(settings: GlanceSettings, modifier: Modifier = Modifier) {
    if (!settings.enabled) return
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var battery by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var event by remember { mutableStateOf<NextEvent?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    if (settings.showBattery) {
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    battery = if (level >= 0) (level * 100 / scale) to charging else null
                }
            }
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            onDispose { context.unregisterReceiver(receiver) }
        }
    }
    if (settings.showCalendar) {
        LaunchedEffect(now / (5 * 60_000L)) { event = withContext(Dispatchers.IO) { nextEvent(context) } }
    }

    val alarm = remember(now) {
        if (settings.showNextAlarm) context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime else null
    }
    val tone = LocalWallpaperTone.current
    val textStyle = TextStyle(
        color = tone.onWallpaper,
        shadow = if (tone.isDark) Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 1f), 4f) else null,
    )
    val dateFormat = remember { SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), Locale.getDefault()) }

    Column(modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        val ev = event
        if (ev != null) {
            Text(
                text = ev.title,
                style = textStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                modifier = Modifier.clickable {
                    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, ev.id)
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
            )
        }
        if (settings.showDate) {
            Text(
                text = dateFormat.format(Date(now)),
                style = textStyle.copy(fontSize = if (ev == null) 22.sp else 15.sp, fontWeight = if (ev == null) FontWeight.Medium else FontWeight.Normal),
                modifier = Modifier.clickable {
                    runCatching {
                        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (alarm != null && alarm - now < 24 * 60 * 60 * 1000L) {
                Text(
                    text = "⏰ " + DateFormat.getTimeFormat(context).format(Date(alarm)),
                    style = textStyle.copy(fontSize = 14.sp),
                    modifier = Modifier.clickable {
                        runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                )
            }
            val b = battery
            if (b != null && (b.second || b.first <= 20)) {
                Text(
                    text = (if (b.second) "⚡ Charging · " else "🔋 ") + "${b.first}%",
                    style = textStyle.copy(fontSize = 14.sp),
                )
            }
        }
    }
}

private fun nextEvent(context: Context): NextEvent? {
    if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, now)
        ContentUris.appendId(it, now + 24 * 60 * 60 * 1000L)
    }.build()
    val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.ALL_DAY)
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(3) == 1) continue
                val begin = c.getLong(1)
                if (begin < now) continue
                val time = DateFormat.getTimeFormat(context).format(Date(begin))
                return@use NextEvent("${c.getString(0) ?: "Event"} · $time", begin, c.getLong(2))
            }
            null
        }
    }.getOrNull()
}
