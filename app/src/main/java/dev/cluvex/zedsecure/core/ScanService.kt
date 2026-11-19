package dev.cluvex.zedsecure.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.cluvex.zedsecure.MainActivity
import dev.cluvex.zedsecure.R
import dev.cluvex.zedsecure.scan.DnsScanHub
import dev.cluvex.zedsecure.scan.ScanState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            DnsScanHub.stop()
        }
        createChannel()
        val first = build(DnsScanHub.state.value)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, first)
            }
        }.onFailure {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop == null) {
            loop = scope.launch {
                val nm = getSystemService(NotificationManager::class.java)
                delay(1500)
                while (isActive) {
                    val s = DnsScanHub.state.value
                    if (!s.running && !DnsScanHub.e2eRunning) break
                    runCatching { nm?.notify(NOTIFICATION_ID, build(s)) }
                    delay(1000)
                }
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.scan_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun build(s: ScanState): android.app.Notification {
        val p = s.progress
        val text = when {
            s.running && p != null -> getString(R.string.scan_notif_progress, (p.done + p.skipped).toInt(), (p.planned + p.skipped).toInt(), p.good.toInt())
            s.e2eQueued > 0 -> getString(R.string.scan_notif_tunnel, s.e2eDone, s.e2eQueued, s.e2ePassed)
            else -> getString(R.string.scan_notif_starting)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_zed)
            .setContentTitle(getString(R.string.scan_notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, getString(R.string.scan_notif_stop), stop)
        if (s.running && p != null && p.planned + p.skipped > 0 && !p.sweepDone) {
            b.setProgress(1000, (p.fraction * 1000).toInt(), false)
        } else if (s.running || s.e2eQueued > s.e2eDone) {
            b.setProgress(0, 0, true)
        }
        return b.build()
    }

    companion object {
        private const val CHANNEL_ID = "dns_scan"
        private const val NOTIFICATION_ID = 0x5CA9
        private const val ACTION_STOP = "dev.cluvex.zedsecure.SCAN_STOP"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ScanService::class.java)) }
        }
    }
}
