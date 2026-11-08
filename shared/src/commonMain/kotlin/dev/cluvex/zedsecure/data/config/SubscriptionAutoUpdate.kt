package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.data.net.PingCoordinator
import dev.cluvex.zedsecure.domain.model.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SubscriptionAutoUpdate {
    data class AfterUpdate(
        val test: Boolean,
        val removeInvalid: Boolean,
        val sort: Boolean,
        val concurrency: Int,
        val delayUrl: String,
        val detectCountry: Boolean,
    )

    private val lock = Mutex()

    fun afterUpdateOf(settings: AppSettings) = AfterUpdate(
        test = settings.autoTestAfterUpdate,
        removeInvalid = settings.autoRemoveInvalidAfterTest,
        sort = settings.autoSortAfterTest,
        concurrency = settings.realPingConcurrency,
        delayUrl = settings.delayTestUrl,
        detectCountry = settings.detectServerLocation,
    )

    suspend fun runDue(repository: ConfigRepository, settings: AppSettings): Int = lock.withLock {
        if (!settings.autoUpdateSubscriptions) return@withLock 0
        val updated = repository.updateDueSubscriptions(settings.subscriptionUpdateIntervalHours)
        if (updated > 0 && startTest(repository, afterUpdateOf(settings))) {
            PingCoordinator.progress.first { it == null }
        }
        updated
    }

    fun startTest(repository: ConfigRepository, plan: AfterUpdate): Boolean {
        if (!plan.test || PingCoordinator.progress.value != null) return false
        val profiles = repository.profiles.value
        if (profiles.isEmpty()) return false
        PingCoordinator.start(
            profiles = profiles,
            useRealDelay = true,
            concurrency = plan.concurrency,
            delayUrl = plan.delayUrl,
            chainConfig = repository::chainProbeConfig,
            clearPings = { repository.clearPings(it, persist = false) },
            detectCountry = plan.detectCountry,
            onResult = { id, ms, cc -> repository.setPing(id, ms, cc, persist = false) },
            flush = { repository.flushProfiles() },
            onFinished = { afterTest(repository, plan.removeInvalid, plan.sort) },
        )
        return true
    }

    fun afterTest(repository: ConfigRepository, removeInvalid: Boolean, sort: Boolean) {
        val reachable = repository.profiles.value.any { (it.lastPingMs ?: 0) > 0 }
        if (removeInvalid && reachable) repository.removeInvalid()
        if (sort) repository.sortByTestResults()
    }
}
