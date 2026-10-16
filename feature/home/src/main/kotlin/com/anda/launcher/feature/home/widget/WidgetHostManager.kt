package com.anda.launcher.feature.home.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.anda.launcher.core.apps.UserResolver
import kotlin.math.hypot

/** Long-press + drag bridge from a widget's View hierarchy into the Compose drag system. */
interface WidgetTouchListener {
    fun onLongPress(rawX: Float, rawY: Float)
    fun onMove(rawX: Float, rawY: Float)
    fun onRelease()
}

/** Widget view that detects long-press itself (widgets consume their own touches). */
class LauncherWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var touchListener: WidgetTouchListener? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private val longPress = Runnable {
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        parent?.requestDisallowInterceptTouchEvent(true)
        touchListener?.onLongPress(downX, downY)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                downX = ev.rawX
                downY = ev.rawY
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (!longPressed && hypot(ev.rawX - downX, ev.rawY - downY) > slop) handler.removeCallbacks(longPress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
        }
        return longPressed
    }

    @SuppressLint("ClickableViewAccessibility") // Accessibility users get the item's custom actions instead.
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!longPressed) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> touchListener?.onMove(ev.rawX, ev.rawY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressed = false
                touchListener?.onRelease()
            }
        }
        return true
    }
}

private class LauncherAppWidgetHost(context: Context, id: Int) : AppWidgetHost(context, id) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LauncherWidgetHostView(context)
}

/**
 * Owns the AppWidgetHost, widget id lifecycle and a view cache so pages scrolling in and out of
 * composition reuse the same widget views (no re-inflation jank).
 */
class WidgetHostManager(context: Context, private val users: UserResolver) {
    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    private val host: AppWidgetHost = LauncherAppWidgetHost(appContext, HOST_ID)
    private val views = HashMap<Int, LauncherWidgetHostView>()

    fun startListening() {
        runCatching { host.startListening() }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        views.remove(id)
        runCatching { host.deleteAppWidgetId(id) }
    }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun providers(): List<AppWidgetProviderInfo> =
        users.profiles().flatMap { manager.getInstalledProvidersForProfile(it) }

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)

    fun bindIntent(id: Int, info: AppWidgetProviderInfo): Intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)

    fun needsConfiguration(info: AppWidgetProviderInfo): Boolean =
        info.configure != null && (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) == 0

    fun isReconfigurable(info: AppWidgetProviderInfo): Boolean =
        info.configure != null && (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0

    fun startConfigure(activity: Activity, id: Int, requestCode: Int): Boolean = runCatching {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
        true
    }.getOrDefault(false)

    /** Returns a cached host view, detached from any previous parent. */
    fun view(context: Context, id: Int): LauncherWidgetHostView? {
        views[id]?.let { cached ->
            (cached.parent as? ViewGroup)?.removeView(cached)
            return cached
        }
        val info = info(id) ?: return null
        val view = host.createView(context, id, info) as? LauncherWidgetHostView ?: return null
        views[id] = view
        return view
    }

    /** Reports the widget's real size so responsive (Android 12+) layouts pick the right variant. */
    fun updateSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
        runCatching { view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp))) }
    }

    /** Views hold an Activity context: drop them when the activity is destroyed. */
    fun clearViews() {
        views.values.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        views.clear()
    }

    /** Default span in cells for a provider. */
    fun defaultSpan(info: AppWidgetProviderInfo, columns: Int, rows: Int, cellWidthDp: Float, cellHeightDp: Float): Pair<Int, Int> {
        val density = appContext.resources.displayMetrics.density
        val x = info.targetCellWidth.takeIf { it > 0 } ?: ((info.minWidth / density) / cellWidthDp).toInt().coerceAtLeast(0) + 1
        val y = info.targetCellHeight.takeIf { it > 0 } ?: ((info.minHeight / density) / cellHeightDp).toInt().coerceAtLeast(0) + 1
        return x.coerceIn(1, columns) to y.coerceIn(1, rows)
    }

    fun minSpan(info: AppWidgetProviderInfo, cellWidthDp: Float, cellHeightDp: Float): Pair<Int, Int> {
        val density = appContext.resources.displayMetrics.density
        val w = (info.minResizeWidth.takeIf { it > 0 } ?: info.minWidth) / density
        val h = (info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight) / density
        return ((w / cellWidthDp).toInt() + if (w % cellWidthDp > 1f) 1 else 0).coerceAtLeast(1) to
            ((h / cellHeightDp).toInt() + if (h % cellHeightDp > 1f) 1 else 0).coerceAtLeast(1)
    }

    companion object {
        const val HOST_ID = 1024
        const val REQUEST_BIND = 2001
        const val REQUEST_CONFIGURE = 2002
    }
}
