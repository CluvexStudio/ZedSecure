package dev.cluvex.zedsecure.core

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import dev.cluvex.zedsecure.R
import dev.cluvex.zedsecure.core.platform.LocaleManager
import dev.cluvex.zedsecure.data.settings.SettingsRepository
import dev.cluvex.zedsecure.domain.model.AppDesign
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.domain.model.ThemeMode
import dev.cluvex.zedsecure.ui.bento.BentoColors
import dev.cluvex.zedsecure.ui.bento.BentoPalettes
import dev.cluvex.zedsecure.ui.format.formatRate

open class ZedWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        push(ctx, mgr, ids, smallByDefault = this is ZedWidgetToggleProvider)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        push(ctx, mgr, intArrayOf(id), smallByDefault = this is ZedWidgetToggleProvider)
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
        private const val DASH = "—"

        private val SMALL = SizeF(100f, 100f)
        private val MEDIUM = SizeF(160f, 48f)
        private val LARGE = SizeF(240f, 140f)

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
                val panel = mgr.getAppWidgetIds(ComponentName(ctx, ZedWidgetProvider::class.java))
                val toggle = mgr.getAppWidgetIds(ComponentName(ctx, ZedWidgetToggleProvider::class.java))
                if (panel.isNotEmpty()) push(ctx, mgr, panel, smallByDefault = false)
                if (toggle.isNotEmpty()) push(ctx, mgr, toggle, smallByDefault = true)
            }
        }

        private fun push(ctx: Context, mgr: AppWidgetManager, ids: IntArray, smallByDefault: Boolean) {
            runCatching {
                val local = localized(ctx)
                val snap = snapshot(local)
                val theme = theme(ctx)
                ids.forEach { id -> mgr.updateAppWidget(id, render(local, snap, theme, mgr, id, smallByDefault)) }
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

        private fun connectsInPlace(ctx: Context): Boolean = runCatching {
            val profile = (ctx.applicationContext as dev.cluvex.zedsecure.ZedSecureApp)
                .container.configRepository.activeProfile()
            profile != null && profile.ikev2Settings() == null && android.net.VpnService.prepare(ctx) == null
        }.getOrDefault(false)

        private fun connectAction(ctx: Context): PendingIntent {
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            if (!connectsInPlace(ctx)) return PendingIntent.getActivity(ctx, REQ_TOGGLE, connectIntent(ctx), flags)
            val start = Intent(ctx, ZedVpnService::class.java)
                .putExtra(VpnManager.EXTRA_COMMAND, VpnManager.CMD_START_ACTIVE)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(ctx, REQ_TOGGLE, start, flags)
            } else {
                PendingIntent.getService(ctx, REQ_TOGGLE, start, flags)
            }
        }

        private class Snapshot(
            val state: ConnectionState,
            val label: String,
            val server: String,
            val flag: String,
            val engine: String?,
            val down: String,
            val up: String,
            val action: String,
            val on: Boolean,
            val connected: Boolean,
            val busy: Boolean,
            val timerBase: Long,
        )

        private fun snapshot(ctx: Context): Snapshot {
            val s = VpnManager.status.value
            val profile = runCatching {
                (ctx.applicationContext as dev.cluvex.zedsecure.ZedSecureApp).container.configRepository.activeProfile()
            }.getOrNull()
            val on = s.state.isActive || s.state.isTransitioning
            val name = s.serverName?.takeIf { it.isNotBlank() }
                ?: profile?.name?.takeIf { it.isNotBlank() }
                ?: ctx.getString(R.string.widget_no_server)
            return Snapshot(
                state = s.state,
                label = ctx.getString(
                    when (s.state) {
                        ConnectionState.Connected -> R.string.state_connected
                        ConnectionState.Connecting -> R.string.state_connecting
                        ConnectionState.Reconnecting -> R.string.state_reconnecting
                        ConnectionState.Disconnecting -> R.string.state_disconnecting
                        ConnectionState.Error -> R.string.state_error
                        ConnectionState.Idle -> R.string.state_idle
                    },
                ),
                server = BidiText.auto(name),
                flag = flagOf(profile?.countryCode),
                engine = profile?.engineLabel,
                down = rate(s.downloadBps),
                up = rate(s.uploadBps),
                action = ctx.getString(if (on) R.string.action_disconnect else R.string.action_connect),
                on = on,
                connected = s.state == ConnectionState.Connected,
                busy = s.state.isTransitioning,
                timerBase = SystemClock.elapsedRealtime() - s.durationSeconds * 1000L,
            )
        }

        private fun rate(bps: Long): String {
            val (value, unit) = formatRate(bps)
            return "$value $unit".latin()
        }

        private fun String.latin(): String = buildString(length) {
            for (ch in this@latin) {
                append(
                    when (ch) {
                        in '۰'..'۹' -> '0' + (ch - '۰')
                        in '٠'..'٩' -> '0' + (ch - '٠')
                        '٫' -> '.'
                        '٬' -> ','
                        else -> ch
                    },
                )
            }
        }

        private fun flagOf(code: String?): String {
            val cc = code?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } } ?: return ""
            return cc.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        }

        private class Palette(
            val surface: Int,
            val ink: Int,
            val muted: Int,
            val stage: Int,
            val onStage: Int,
            val chip: Int,
            val accent: Int,
            val onAccent: Int,
            val tileA: Int,
            val onTileA: Int,
            val tileB: Int,
            val onTileB: Int,
            val good: Int,
            val error: Int,
        )

        private class Theme(val light: Palette, val dark: Palette, val follow: Boolean, val night: Boolean) {
            val now: Palette get() = if (night) dark else light
        }

        private fun fromBento(c: BentoColors) = Palette(
            surface = c.surface.toArgb(),
            ink = c.ink.toArgb(),
            muted = c.muted.toArgb(),
            stage = c.stage.toArgb(),
            onStage = c.onStage.toArgb(),
            chip = c.chipOnStage.toArgb(),
            accent = c.lime.toArgb(),
            onAccent = c.onLime.toArgb(),
            tileA = c.blue.container.toArgb(),
            onTileA = c.blue.content.toArgb(),
            tileB = c.violet.container.toArgb(),
            onTileB = c.violet.content.toArgb(),
            good = c.good.toArgb(),
            error = c.bad.toArgb(),
        )

        private val brandLight by lazy { fromBento(BentoPalettes.BrandLight) }
        private val brandDark by lazy { fromBento(BentoPalettes.BrandDark) }

        private fun theme(ctx: Context): Theme {
            val systemNight = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val settings = runCatching {
                (ctx.applicationContext as dev.cluvex.zedsecure.ZedSecureApp).container.settingsRepository.settings.value
            }.getOrNull() ?: return Theme(brandLight, brandDark, follow = true, night = systemNight)
            val light = runCatching { palette(ctx, settings, night = false) }.getOrDefault(brandLight)
            val dark = runCatching { palette(ctx, settings, night = true) }.getOrDefault(brandDark)
            return when (settings.themeMode) {
                ThemeMode.System -> Theme(light, dark, follow = true, night = systemNight)
                ThemeMode.Light -> Theme(light, light, follow = false, night = false)
                ThemeMode.Dark -> Theme(dark, dark, follow = false, night = true)
            }
        }

        private fun palette(ctx: Context, settings: AppSettings, night: Boolean): Palette {
            val dynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val seed = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                androidx.compose.ui.graphics.Color(ctx.getColor(android.R.color.system_accent1_500))
            } else {
                dev.cluvex.zedsecure.ui.theme.accentSeed(settings.accentColor)
            }
            if (settings.appDesign == AppDesign.Bento) {
                return fromBento(BentoPalettes.of(settings.bento.palette, night, seed, false))
            }
            val s = dev.cluvex.zedsecure.ui.theme.zedColorScheme(night, settings.accentColor, settings.colorStyle, settings.colorContrast)
            val primary = if (dynamic) seed else s.primary
            return Palette(
                surface = s.surfaceContainerHigh.toArgb(),
                ink = s.onSurface.toArgb(),
                muted = s.onSurfaceVariant.toArgb(),
                stage = s.primaryContainer.toArgb(),
                onStage = s.onPrimaryContainer.toArgb(),
                chip = s.onPrimaryContainer.copy(alpha = 0.14f).toArgb(),
                accent = primary.toArgb(),
                onAccent = s.onPrimary.toArgb(),
                tileA = s.secondaryContainer.toArgb(),
                onTileA = s.onSecondaryContainer.toArgb(),
                tileB = s.tertiaryContainer.toArgb(),
                onTileB = s.onTertiaryContainer.toArgb(),
                good = s.tertiary.toArgb(),
                error = s.error.toArgb(),
            )
        }

        private class Paint(private val views: RemoteViews, private val theme: Theme) {
            fun tint(id: Int, pick: (Palette) -> Int) = put(id, "setColorFilter", pick)

            fun text(id: Int, pick: (Palette) -> Int) = put(id, "setTextColor", pick)

            private fun put(id: Int, method: String, pick: (Palette) -> Int) {
                if (theme.follow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColorInt(id, method, pick(theme.light), pick(theme.dark))
                } else {
                    views.setInt(id, method, pick(theme.now))
                }
            }
        }

        private fun render(
            ctx: Context,
            snap: Snapshot,
            theme: Theme,
            mgr: AppWidgetManager,
            id: Int,
            smallByDefault: Boolean,
        ): RemoteViews {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return RemoteViews(
                    mapOf(
                        SMALL to small(ctx, snap, theme),
                        MEDIUM to medium(ctx, snap, theme),
                        LARGE to large(ctx, snap, theme),
                    ),
                )
            }
            val options = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
            val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
            val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
            return when {
                width == 0 || height == 0 -> if (smallByDefault) small(ctx, snap, theme) else large(ctx, snap, theme)
                height < SMALL.height -> medium(ctx, snap, theme)
                width < LARGE.width || height < LARGE.height -> small(ctx, snap, theme)
                else -> large(ctx, snap, theme)
            }
        }

        private fun large(ctx: Context, snap: Snapshot, theme: Theme): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_bento_large)
            val p = Paint(v, theme)
            p.tint(R.id.wb_bg) { it.stage }
            p.tint(R.id.wb_chip_bg) { if (snap.connected) it.accent else it.chip }
            p.text(R.id.wb_state) { if (snap.connected) it.onAccent else it.onStage }
            v.setTextViewText(R.id.wb_state, snap.label)
            timer(v, snap)
            p.text(R.id.wb_timer) { it.onStage }
            flag(v, snap)
            v.setTextViewText(R.id.wb_server, snap.server)
            p.text(R.id.wb_server) { it.onStage }
            val meta = snap.engine?.takeIf { it.isNotBlank() }
            v.setTextViewText(R.id.wb_meta, meta.orEmpty())
            v.setViewVisibility(R.id.wb_meta, if (meta == null) View.GONE else View.VISIBLE)
            p.text(R.id.wb_meta) { it.onStage }
            p.tint(R.id.wb_tile_a_bg) { it.tileA }
            p.text(R.id.wb_down_label) { it.onTileA }
            p.text(R.id.wb_down) { it.onTileA }
            p.tint(R.id.wb_tile_b_bg) { it.tileB }
            p.text(R.id.wb_up_label) { it.onTileB }
            p.text(R.id.wb_up) { it.onTileB }
            v.setTextViewText(R.id.wb_down, BidiText.ltr(if (snap.connected) snap.down else DASH))
            v.setTextViewText(R.id.wb_up, BidiText.ltr(if (snap.connected) snap.up else DASH))
            p.tint(R.id.wb_power_bg) { if (snap.busy) it.chip else it.accent }
            p.tint(R.id.wb_power_icon) { if (snap.busy) it.accent else it.onAccent }
            clicks(ctx, v, snap, rootToggles = false)
            return v
        }

        private fun medium(ctx: Context, snap: Snapshot, theme: Theme): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_bento_medium)
            val p = Paint(v, theme)
            p.tint(R.id.wb_bg) { it.surface }
            flag(v, snap)
            v.setTextViewText(R.id.wb_server, snap.server)
            p.text(R.id.wb_server) { it.ink }
            val line = if (snap.connected) {
                "${snap.label} · ${BidiText.ltr("↓ ${snap.down}")} · ${BidiText.ltr("↑ ${snap.up}")}"
            } else {
                snap.label
            }
            v.setTextViewText(R.id.wb_line, line)
            p.text(R.id.wb_line) { it.muted }
            p.tint(R.id.wb_dot) {
                when {
                    snap.connected -> it.good
                    snap.state == ConnectionState.Error -> it.error
                    snap.busy -> it.accent
                    else -> it.muted
                }
            }
            p.tint(R.id.wb_power_bg) { if (snap.connected) it.accent else it.stage }
            p.tint(R.id.wb_power_icon) { if (snap.connected) it.onAccent else it.accent }
            clicks(ctx, v, snap, rootToggles = false)
            return v
        }

        private fun small(ctx: Context, snap: Snapshot, theme: Theme): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_bento_small)
            val p = Paint(v, theme)
            val ink: (Palette) -> Int = {
                when {
                    snap.connected -> it.onAccent
                    snap.busy -> it.ink
                    else -> it.onStage
                }
            }
            p.tint(R.id.wb_bg) {
                when {
                    snap.connected -> it.accent
                    snap.busy -> it.surface
                    else -> it.stage
                }
            }
            p.tint(R.id.wb_power_bg) {
                when {
                    snap.connected -> it.onAccent
                    snap.busy -> it.stage
                    else -> it.accent
                }
            }
            p.tint(R.id.wb_power_icon) { if (snap.connected || snap.busy) it.accent else it.onAccent }
            v.setTextViewText(R.id.wb_state, if (snap.connected || snap.busy) snap.label else snap.action)
            p.text(R.id.wb_state, ink)
            timer(v, snap)
            p.text(R.id.wb_timer, ink)
            v.setTextViewText(R.id.wb_sub, snap.server)
            v.setViewVisibility(R.id.wb_sub, if (snap.connected) View.GONE else View.VISIBLE)
            p.text(R.id.wb_sub, ink)
            clicks(ctx, v, snap, rootToggles = true)
            return v
        }

        private fun timer(v: RemoteViews, snap: Snapshot) {
            if (snap.connected) {
                v.setChronometer(R.id.wb_timer, snap.timerBase, null, true)
                v.setViewVisibility(R.id.wb_timer, View.VISIBLE)
            } else {
                v.setChronometer(R.id.wb_timer, SystemClock.elapsedRealtime(), null, false)
                v.setViewVisibility(R.id.wb_timer, View.GONE)
            }
        }

        private fun flag(v: RemoteViews, snap: Snapshot) {
            v.setTextViewText(R.id.wb_flag, snap.flag)
            v.setViewVisibility(R.id.wb_flag, if (snap.flag.isEmpty()) View.GONE else View.VISIBLE)
        }

        private fun clicks(ctx: Context, v: RemoteViews, snap: Snapshot, rootToggles: Boolean) {
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val toggle = if (snap.on) {
                PendingIntent.getBroadcast(
                    ctx,
                    REQ_TOGGLE,
                    Intent(ctx, ZedWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                    flags,
                )
            } else {
                connectAction(ctx)
            }
            v.setOnClickPendingIntent(R.id.wb_power, toggle)
            v.setContentDescription(R.id.wb_power, snap.action)
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?: Intent(ctx, dev.cluvex.zedsecure.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(
                R.id.wb_root,
                if (rootToggles) toggle else PendingIntent.getActivity(ctx, REQ_OPEN, open, flags),
            )
        }
    }
}

class ZedWidgetToggleProvider : ZedWidgetProvider()
