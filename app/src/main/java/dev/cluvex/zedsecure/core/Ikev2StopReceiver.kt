package dev.cluvex.zedsecure.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class Ikev2StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return
        if (Ikev2Controller.isActive) Ikev2Controller.stop(context.applicationContext)
        ZedWidgetProvider.refresh(context.applicationContext, force = true)
    }

    companion object {
        private const val ACTION_STOP = "dev.cluvex.zedsecure.IKEV2_STOP"
        private const val REQ_STOP = 3

        fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQ_STOP,
                Intent(context, Ikev2StopReceiver::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
