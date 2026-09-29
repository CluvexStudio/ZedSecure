package dev.cluvex.zedsecure.core

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.widget.RemoteViews
import dev.cluvex.zedsecure.R
import dev.cluvex.zedsecure.core.platform.LocaleManager
import dev.cluvex.zedsecure.data.settings.SettingsRepository
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.ui.format.formatBytes

class ZedWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val views = render(localized(ctx))
        ids.forEach { mgr.updateAppWidget(it, views) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_TOGGLE) toggle(ctx)
    }

    private fun toggle(ctx: Context) {
        val state = VpnManager.status.value.state
        if (state.isActive || state.isTransitioning) {
            if (Ikev2Controller.isActive) Ikev2Controller.stop(ctx) else AndroidVpn.stop(ctx)
            refresh(ctx, force = true)
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "dev.cluvex.zedsecure.widget.TOGGLE"
        private const val REQ_TOGGLE = 100
        private const val REQ_OPEN = 101

        private const val MIN_INTERVAL_MS = 900L

        private const val COMPACT_W = 110f
        private const val STANDARD_W = 250f
        private const val ROW_H = 110f

        @Volatile private var lastPush = 0L

        fun refresh(ctx: Context, force: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastPush < MIN_INTERVAL_MS) return
            lastPush = now
            pushNow(ctx)
        }

        private fun pushNow(ctx: Context) {
            runCatching {
                val mgr = AppWidgetManager.getInstance(ctx) ?: return
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, ZedWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val views = render(localized(ctx))
                ids.forEach { mgr.updateAppWidget(it, views) }
            }
        }

        private fun localized(ctx: Context): Context =
            runCatching { LocaleManager.wrap(ctx, SettingsRepository.readLanguageTag(ctx)) }
                .getOrDefault(ctx)

        private fun connectIntent(ctx: Context): Intent =
            Intent(ctx, dev.cluvex.zedsecure.MainActivity::class.java).apply {
                action = VpnNotifications.ACTION_CONNECT
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        private data class Snapshot(
            val state: String,
            val server: String,
            val down: String,
            val up: String,
            val action: String,
            val on: Boolean,
            val icon: Int,
            val dotColor: Int,
        )

        private fun snapshot(ctx: Context): Snapshot {
            val s = VpnManager.status.value
            val on = s.state.isActive || s.state.isTransitioning
            return Snapshot(
                state = ctx.getString(
                    when (s.state) {
                        ConnectionState.Connected -> R.string.state_connected
                        ConnectionState.Connecting -> R.string.state_connecting
                        ConnectionState.Reconnecting -> R.string.state_reconnecting
                        ConnectionState.Disconnecting -> R.string.state_disconnecting
                        ConnectionState.Error -> R.string.state_error
                        ConnectionState.Idle -> R.string.state_idle
                    },
                ),

                server = BidiText.auto(
                    s.serverName?.takeIf { it.isNotBlank() }
                        ?: ctx.getString(R.string.widget_no_server),
                ),
                down = BidiText.ltr(formatBytes(s.totalDownload)),
                up = BidiText.ltr(formatBytes(s.totalUpload)),
                action = ctx.getString(if (on) R.string.action_disconnect else R.string.action_connect),
                on = on,
                icon = if (s.state == ConnectionState.Connected) R.drawable.ic_lock else R.drawable.ic_lock_open,
                dotColor = ctx.getColor(
                    when (s.state) {
                        ConnectionState.Connected -> R.color.widget_ok
                        ConnectionState.Error -> R.color.widget_warn
                        ConnectionState.Idle -> R.color.widget_text_dim
                        else -> R.color.widget_accent
                    },
                ),
            )
        }

        private fun render(ctx: Context): RemoteViews {
            val snap = snapshot(ctx)
            val standard = build(ctx, snap, R.layout.widget_zed, rich = true)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return standard
            val compact = build(ctx, snap, R.layout.widget_zed_compact, rich = false)
            return RemoteViews(
                mapOf(
                    SizeF(COMPACT_W, ROW_H) to compact,
                    SizeF(STANDARD_W, ROW_H) to standard,
                ),
            )
        }

        private fun build(ctx: Context, snap: Snapshot, layout: Int, rich: Boolean): RemoteViews {
            val views = RemoteViews(ctx.packageName, layout)

            views.setTextViewText(R.id.widget_state, snap.state)
            views.setTextViewText(R.id.widget_server, snap.server)
            views.setImageViewResource(R.id.widget_icon, snap.icon)
            views.setTextViewText(R.id.widget_button, snap.action)
            views.setContentDescription(R.id.widget_button, snap.action)
            views.setInt(
                R.id.widget_button, "setBackgroundResource",
                if (snap.on) R.drawable.widget_button_on else R.drawable.widget_button_off,
            )

            if (rich) {
                views.setTextViewText(R.id.widget_down, snap.down)
                views.setTextViewText(R.id.widget_up, snap.up)

                views.setInt(R.id.widget_dot, "setColorFilter", snap.dotColor)
            }

            views.setOnClickPendingIntent(
                R.id.widget_button,
                if (snap.on) {
                    PendingIntent.getBroadcast(
                        ctx, REQ_TOGGLE,
                        Intent(ctx, ZedWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                } else {
                    PendingIntent.getActivity(
                        ctx, REQ_TOGGLE, connectIntent(ctx),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                },
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    ctx, REQ_OPEN,
                    ctx.packageManager.getLaunchIntentForPackage(ctx.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return views
        }
    }
}
