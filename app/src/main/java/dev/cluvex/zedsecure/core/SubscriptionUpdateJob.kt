package dev.cluvex.zedsecure.core

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import dev.cluvex.zedsecure.core.AppLog as Log
import dev.cluvex.zedsecure.ZedSecureApp
import dev.cluvex.zedsecure.domain.config.Subscription
import dev.cluvex.zedsecure.domain.config.SubscriptionSchedule
import dev.cluvex.zedsecure.domain.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object SubscriptionUpdateScheduler {
    private const val JOB_ID = 0x5AB5
    private const val TAG = "SubUpdateScheduler"

    fun sync(context: Context, settings: AppSettings, subscriptions: List<Subscription>) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!settings.autoUpdateSubscriptions || subscriptions.none { it.enabled }) {
            if (scheduler.getPendingJob(JOB_ID) != null) scheduler.cancel(JOB_ID)
            return
        }
        val periodMs = SubscriptionSchedule.periodHours(subscriptions, settings.subscriptionUpdateIntervalHours) *
            3_600_000L

        val pending = scheduler.getPendingJob(JOB_ID)
        if (pending != null && pending.intervalMillis == periodMs) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, SubscriptionUpdateJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(periodMs, (periodMs / 4).coerceAtLeast(JobInfo.getMinFlexMillis()))
            .build()
        val result = runCatching { scheduler.schedule(job) }.getOrDefault(JobScheduler.RESULT_FAILURE)
        if (result != JobScheduler.RESULT_SUCCESS) Log.w(TAG, "could not schedule the subscription update job")
    }
}

class SubscriptionUpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val container = (application as ZedSecureApp).container
        val settings = container.settingsRepository.settings.value
        if (!settings.autoUpdateSubscriptions) return false
        running = scope.launch {
            val refreshed = runCatching {
                container.configRepository.updateDueSubscriptions(settings.subscriptionUpdateIntervalHours)
            }.getOrDefault(0)
            if (refreshed > 0) Log.i(TAG, "auto-updated $refreshed subscription(s)")

            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SubUpdateJob"
    }
}
