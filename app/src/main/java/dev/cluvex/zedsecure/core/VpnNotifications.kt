package dev.cluvex.zedsecure.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import dev.cluvex.zedsecure.domain.model.NotifChip
import androidx.core.graphics.drawable.IconCompat
import dev.cluvex.zedsecure.R
import dev.cluvex.zedsecure.ui.format.formatBytes
import dev.cluvex.zedsecure.ui.format.formatRate

class VpnNotifications(private val ctx: Context) {
    enum class Stage { Preparing, Interface, Engine, Tunnel, Connected }

    data class Colors(val accent: Int, val deep: Int, val good: Int, val error: Int)

    private var colorsKey: Any? = null
    private var colorsCache: Colors? = null

    private fun colors(): Colors {
        val settings = runCatching { (ctx.applicationContext as dev.cluvex.zedsecure.ZedSecureApp).container.settingsRepository.settings.value }
            .getOrNull() ?: return FALLBACK
        val night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val key = listOf(settings.appDesign, settings.bento.palette, settings.accentColor, settings.colorStyle, settings.colorContrast, settings.dynamicColor, night)
        colorsCache?.takeIf { key == colorsKey }?.let { return it }
        val computed = runCatching { themeColors(settings, night) }.getOrDefault(FALLBACK)
        colorsKey = key
        colorsCache = computed
        return computed
    }

    private fun themeColors(settings: dev.cluvex.zedsecure.domain.model.AppSettings, night: Boolean): Colors {
        val dynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val seed = if (dynamic) {
            androidx.compose.ui.graphics.Color(ctx.getColor(android.R.color.system_accent1_500))
        } else {
            dev.cluvex.zedsecure.ui.theme.accentSeed(settings.accentColor)
        }
        if (settings.appDesign == dev.cluvex.zedsecure.domain.model.AppDesign.Bento) {
            val c = dev.cluvex.zedsecure.ui.bento.BentoPalettes.of(settings.bento.palette, night, seed, false)
            return Colors(c.lime.toArgb(), c.stage.toArgb(), c.good.toArgb(), c.bad.toArgb())
        }
        val scheme = dev.cluvex.zedsecure.ui.theme.zedColorScheme(night, settings.accentColor, settings.colorStyle, settings.colorContrast)
        val primary = if (dynamic) seed else scheme.primary
        return Colors(primary.toArgb(), scheme.primaryContainer.toArgb(), scheme.tertiary.toArgb(), scheme.error.toArgb())
    }

    private fun String.latin(): String = buildString(length) {
        for (ch in this@latin) {
            append(
                when (ch) {
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    '\u066B' -> '.'
                    '\u066C' -> ','
                    else -> ch
                },
            )
        }
    }

    sealed interface Phase {
        data class Connecting(val stage: Stage) : Phase
        data object Connected : Phase

        data class Failed(val reason: String, val retryable: Boolean = true) : Phase
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.notif_channel_name),

            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = ctx.getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun build(
        server: String,
        phase: Phase,
        protocolLabel: String = "",
        downBps: Long = 0,
        upBps: Long = 0,
        totalDown: Long = 0,
        totalUp: Long = 0,
        connectedAt: Long = 0,
        showSpeed: Boolean = true,
        livePromotion: Boolean = true,
        chip: NotifChip = NotifChip.Speed,
    ): android.app.Notification {
        val title = BidiText.auto(server.ifBlank { ctx.getString(R.string.app_name) })
        val colors = colors()
        val b = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_zed)
            .setContentTitle(title)
            .setColor(colors.accent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp())

            .setRequestPromotedOngoing(livePromotion)

        return when (phase) {
            is Phase.Connecting -> b.applyConnecting(phase.stage, protocolLabel, colors).build()
            Phase.Connected -> b.applyConnected(
                protocolLabel, downBps, upBps, totalDown, totalUp, connectedAt, showSpeed, chip,
                title,
            ).build()
            is Phase.Failed -> b.applyFailed(phase, colors).build()
        }
    }

    private fun NotificationCompat.Builder.applyConnecting(
        stage: Stage,
        protocolLabel: String,
        colors: Colors,
    ): NotificationCompat.Builder {
        val step = stage.ordinal.coerceIn(0, STAGE_COUNT)
        val progress = step * SEGMENT_LEN
        val stageText = ctx.getString(stageLabel(stage))

        val style = NotificationCompat.ProgressStyle()
            .setProgressSegments(
                listOf(colors.deep, colors.deep, colors.accent, colors.good)
                    .map { NotificationCompat.ProgressStyle.Segment(SEGMENT_LEN).setColor(it) },
            )
            .setProgressPoints(
                (1 until STAGE_COUNT).map { i ->
                    NotificationCompat.ProgressStyle.Point(i * SEGMENT_LEN).setColor(colors.accent)
                },
            )
            .setProgress(progress)
            .setProgressTrackerIcon(IconCompat.createWithResource(ctx, R.drawable.ic_bolt))
            .setProgressStartIcon(IconCompat.createWithResource(ctx, R.drawable.ic_home))
            .setProgressEndIcon(IconCompat.createWithResource(ctx, R.drawable.ic_public))

        return setContentText(stageText)
            .setStyle(style)

            .setShortCriticalText(ctx.getString(R.string.notif_chip_connecting))
            .setSubText(protocolLabel.takeIf { it.isNotBlank() }?.let(BidiText::ltr))
            .setShowWhen(false)
            .addAction(R.drawable.ic_close, ctx.getString(R.string.action_cancel), stopTunnel())
    }

    private fun NotificationCompat.Builder.applyConnected(
        protocolLabel: String,
        downBps: Long,
        upBps: Long,
        totalDown: Long,
        totalUp: Long,
        connectedAt: Long,
        showSpeed: Boolean,
        chip: NotifChip,
        configName: String,
    ): NotificationCompat.Builder {
        val (dl, dlU) = formatRate(downBps).let { (n, u) -> n.latin() to u }
        val (ul, ulU) = formatRate(upBps).let { (n, u) -> n.latin() to u }

        val speeds = BidiText.ltr(ctx.getString(R.string.notif_speeds, "$dl $dlU", "$ul $ulU"))

        val totals = ctx.getString(
            R.string.notif_totals,
            BidiText.ltr(formatBytes(totalDown).latin()),
            BidiText.ltr(formatBytes(totalUp).latin()),
        )
        val headline = if (showSpeed) speeds else ctx.getString(R.string.state_connected)

        val detail = if (showSpeed) "$speeds\n$totals" else totals

        return setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))

            .setShortCriticalText(
                chipText(chip, showSpeed, "$dl$dlU", totalDown + totalUp, connectedAt, configName),
            )
            .setSubText(protocolLabel.takeIf { it.isNotBlank() }?.let(BidiText::ltr))

            .setShowWhen(connectedAt > 0)
            .setWhen(connectedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
            .setUsesChronometer(connectedAt > 0)
            .addAction(R.drawable.ic_close, ctx.getString(R.string.action_disconnect), stopTunnel())
    }

    private fun chipText(
        chip: NotifChip,
        showSpeed: Boolean,
        downRate: String,
        totalBytes: Long,
        connectedAt: Long,
        configName: String,
    ): String = when (chip) {
        NotifChip.Speed ->
            if (showSpeed) BidiText.ltr("↓ $downRate").toString() else ctx.getString(R.string.notif_chip_on)
        NotifChip.ConfigName -> BidiText.auto(shorten(configName)).toString()
        NotifChip.Duration -> BidiText.ltr(elapsedShort(connectedAt)).toString()
        NotifChip.Total -> BidiText.ltr(formatBytes(totalBytes).latin()).toString()
        NotifChip.None -> ctx.getString(R.string.notif_chip_on)
    }

    private fun shorten(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ctx.getString(R.string.app_name)
        return if (trimmed.length <= CHIP_MAX_CHARS) trimmed
        else trimmed.take(CHIP_MAX_CHARS - 1).trimEnd() + "…"
    }

    private fun elapsedShort(connectedAt: Long): String {
        if (connectedAt <= 0) return "0s"
        val seconds = ((System.currentTimeMillis() - connectedAt) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            else -> "${seconds / 3600}h"
        }
    }

    private fun NotificationCompat.Builder.applyFailed(
        phase: Phase.Failed,
        colors: Colors,
    ): NotificationCompat.Builder {
        val reason = BidiText.auto(phase.reason)
        val b = setContentTitle(ctx.getString(R.string.notif_title_disconnected))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setShortCriticalText(ctx.getString(R.string.notif_chip_off))
            .setShowWhen(false)
            .setColor(colors.error)
        if (phase.retryable) {
            b.addAction(R.drawable.ic_sync, ctx.getString(R.string.action_retry), reconnect())
        }
        return b.addAction(R.drawable.ic_close, ctx.getString(R.string.action_disconnect), stopTunnel())
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            ctx, REQ_OPEN, ctx.packageManager.getLaunchIntentForPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun stopTunnel(): PendingIntent =
        PendingIntent.getService(
            ctx, REQ_STOP,
            Intent(ctx, ZedVpnService::class.java)
                .putExtra(VpnManager.EXTRA_COMMAND, VpnManager.CMD_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun reconnect(): PendingIntent =
        PendingIntent.getActivity(
            ctx, REQ_RETRY,
            Intent(ctx, dev.cluvex.zedsecure.MainActivity::class.java).apply {
                action = ACTION_CONNECT
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun stageLabel(stage: Stage): Int = when (stage) {
        Stage.Preparing -> R.string.notif_stage_preparing
        Stage.Interface -> R.string.notif_stage_interface
        Stage.Engine -> R.string.notif_stage_engine
        Stage.Tunnel -> R.string.notif_stage_tunnel
        Stage.Connected -> R.string.state_connected
    }

    companion object {
        private const val CHIP_MAX_CHARS = 10

        const val CHANNEL_ID = "zed_vpn"
        const val NOTIFICATION_ID = 1

        const val ACTION_CONNECT = "dev.cluvex.zedsecure.action.CONNECT"

        private const val REQ_OPEN = 0
        private const val REQ_STOP = 1
        private const val REQ_RETRY = 2

        private const val STAGE_COUNT = 4
        private const val SEGMENT_LEN = 25

        private val FALLBACK = Colors(
            accent = 0xFFC7F24E.toInt(),
            deep = 0xFF311B6B.toInt(),
            good = 0xFF7BE495.toInt(),
            error = 0xFFB3261E.toInt(),
        )
    }
}
