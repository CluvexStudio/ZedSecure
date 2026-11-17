package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.data.net.NetworkInfoRepository
import dev.cluvex.zedsecure.data.net.GeoLookup
import dev.cluvex.zedsecure.platform.resolveHostAddress
import dev.cluvex.zedsecure.data.net.PingService
import dev.cluvex.zedsecure.domain.config.SubscriptionParser
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.platform.KeyValueStore
import dev.cluvex.zedsecure.platform.currentTimeMillis
import dev.cluvex.zedsecure.platform.httpGetResponse
import dev.cluvex.zedsecure.platform.newId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ConfigExplorerEntry(
    val id: String,
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val sourceId: String,
    val sourceName: String,
    val link: String,
    val samplesMs: List<Int>,
    val medianMs: Int,
    val meanMs: Int,
    val jitterMs: Int,
    val lastCheckedAt: Long,
    val profileId: String? = null,
    val ipAddress: String? = null,
    val countryCode: String? = null,
)

@Serializable
data class ConfigExplorerSourceStatus(
    val sourceId: String,
    val name: String,
    val repositoryUrl: String,
    val feedEnabled: Boolean,
    val ok: Boolean,
    val fetchedCount: Int = 0,
    val checkedAt: Long = 0,
    val message: String = "",
)

@Serializable
data class ConfigExplorerSnapshot(
    val enabled: Boolean = true,
    val groupId: String? = null,
    val managedProfileIds: List<String> = emptyList(),
    val healthyPool: List<ConfigExplorerEntry> = emptyList(),
    val liveEntries: List<ConfigExplorerEntry> = emptyList(),
    val sourceStatuses: List<ConfigExplorerSourceStatus> = emptyList(),
    val candidateCount: Int = 0,
    val parsedCount: Int = 0,
    val testedCount: Int = 0,
    val cycleCount: Int = 0,
    val lastRunAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastError: String? = null,
    val isRunning: Boolean = false,
    val phase: String = "idle",
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
)

class ConfigExplorerRepository(private val store: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ConfigExplorerSnapshot> = _state.asStateFlow()

    fun updateRuntime(transform: (ConfigExplorerSnapshot) -> ConfigExplorerSnapshot) {
        _state.update(transform)
    }

    fun setEnabled(enabled: Boolean) {
        val next = _state.value.copy(enabled = enabled)
        _state.value = next
        persist(next)
    }

    fun setGroupId(groupId: String) {
        val next = _state.value.copy(groupId = groupId)
        _state.value = next
        persist(next)
    }

    fun publish(snapshot: ConfigExplorerSnapshot) {
        val next = snapshot.copy(enabled = _state.value.enabled)
        _state.value = next
        persist(next)
    }

    private fun load(): ConfigExplorerSnapshot =
        store.getString(KEY_STATE)?.let { raw ->
            runCatching { json.decodeFromString<ConfigExplorerSnapshot>(raw) }.getOrNull()
        } ?: ConfigExplorerSnapshot()

    private fun persist(snapshot: ConfigExplorerSnapshot) {
        runCatching { store.putString(KEY_STATE, json.encodeToString(snapshot)) }
    }

    private companion object {
        const val KEY_STATE = "zx_config_explorer_state_v1"
    }
}

data class ConfigExplorerSource(
    val id: String,
    val name: String,
    val repositoryUrl: String,
    val feedUrl: String?,
    val description: String,
)

object ConfigExplorerEngine {
    const val INTERVAL_MS = 15 * 60_000L
    const val CANDIDATE_LIMIT = 2_000
    const val HEALTHY_LIMIT = 200
    const val BEST_LIMIT = 100
    private const val RECHECK_BATCH_SIZE = 200
    private const val TEST_CONCURRENCY = 16
    private const val GROUP_NAME = "ZX Explorer · Top 100"
    private const val FIRST_TEST_URL = NetworkInfoRepository.DELAY_TEST_URL
    private const val SECOND_TEST_URL = NetworkInfoRepository.DELAY_TEST_URL2

    private data class Candidate(val source: ConfigExplorerSource, val link: String)
    private data class ParsedCandidate(val candidate: Candidate, val profile: VpnProfile)
    private data class Preliminary(
        val parsed: ParsedCandidate,
        val firstMs: Int?,
        val secondMs: Int?,
        val preferredUrl: String,
    )
    private data class SourceFetch(
        val source: ConfigExplorerSource,
        val links: List<String>,
        val status: ConfigExplorerSourceStatus,
    )

    data class SyncResult(
        val entries: List<ConfigExplorerEntry>,
        val managedProfileIds: List<String>,
    )

    val sources: List<ConfigExplorerSource> = listOf(
        ConfigExplorerSource("mahdibland", "V2RayAggregator", "https://github.com/mahdibland/V2RayAggregator", "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/airport_merge_base64.txt", "فید تجمیعی Base64"),
        ConfigExplorerSource("barry-far", "Barry Far V2ray Config", "https://github.com/barry-far/V2ray-Config", "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Base64/Sub1_base64.txt", "فید اشتراکی Base64"),
        ConfigExplorerSource("radikal", "Free-v2ray-Configs", "https://github.com/0xRadikal/Free-v2ray-Configs", "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/verified/configs.txt", "فهرست verified؛ اعتبار نهایی در ZX دوباره سنجیده می‌شود"),
        ConfigExplorerSource("delta-kronecker", "Delta-Kronecker V2ray Config", "https://github.com/Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/all_configs.txt", "فهرست تجمیع‌شده کانفیگ‌ها"),
        ConfigExplorerSource("v2go", "Danialsamadi v2go", "https://github.com/Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/AllConfigsSub.txt", "فید اشتراکی تجمیع‌شده"),
        ConfigExplorerSource("morpheusadam", "morpheusadam v2ray-config", "https://github.com/morpheusadam/v2ray-config", "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/all.txt", "فهرست عمومی کانفیگ‌ها"),
        ConfigExplorerSource("collector", "V2ray-Collector", "https://github.com/MohammadBahemmat/V2ray-Collector", "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/all_servers.txt", "خروجی جمع‌آوری‌شده سرورها"),
        ConfigExplorerSource("configpilot", "V2Root ConfigPilot", "https://github.com/V2RayRoot/V2Root-ConfigPilot", "https://raw.githubusercontent.com/V2RayRoot/V2Root-ConfigPilot/main/output/BestConfigs.txt", "خروجی منتخب؛ دوباره با هسته ZX آزمایش می‌شود"),
        ConfigExplorerSource("wzdnzd", "wzdnzd aggregator", "https://github.com/wzdnzd/aggregator", null, "ابزار تجمیع؛ فید مستقیم پایدار در این تنظیمات تعریف نشده است"),
        ConfigExplorerSource("whitedns", "WhiteDNS subs-check", "https://github.com/WhiteDNS/subs-check", null, "ابزار بررسی اشتراک؛ منبع مستقیم کانفیگ نیست"),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cycleMutex = Mutex()
    private var schedulerJob: Job? = null

    fun start(repository: ConfigRepository) {
        if (schedulerJob?.isActive == true) return
        schedulerJob = scope.launch {
            while (isActive) {
                val explorer = repository.configExplorer
                if (!explorer.state.value.enabled) {
                    delay(30_000L)
                    continue
                }
                val dueAt = explorer.state.value.lastRunAt?.plus(INTERVAL_MS) ?: 0L
                val waitMs = (dueAt - currentTimeMillis()).coerceAtLeast(0L)
                if (waitMs > 0L) delay(waitMs)
                if (explorer.state.value.enabled) runCycle(repository)
                val nextAt = explorer.state.value.lastRunAt?.plus(INTERVAL_MS)
                    ?: (currentTimeMillis() + INTERVAL_MS)
                delay((nextAt - currentTimeMillis()).coerceAtLeast(1_000L))
            }
        }
    }

    fun runNow(repository: ConfigRepository) {
        if (repository.configExplorer.state.value.enabled) {
            scope.launch { runCycle(repository) }
        }
    }

    fun setEnabled(repository: ConfigRepository, enabled: Boolean) {
        repository.configExplorer.setEnabled(enabled)
        if (enabled) start(repository)
    }

    fun promoteReserve(repository: ConfigRepository, entryId: String) {
        scope.launch {
            cycleMutex.withLock {
                val explorer = repository.configExplorer
                val snapshot = explorer.state.value
                val currentBest = snapshot.healthyPool.take(BEST_LIMIT)
                val reserve = snapshot.healthyPool.drop(BEST_LIMIT)
                val selected = reserve.firstOrNull { it.id == entryId } ?: return@withLock
                if (currentBest.isEmpty()) return@withLock

                val displaced = currentBest.lastOrNull()
                val nextBest = (currentBest.dropLast(1) + selected)
                    .sortedWith(entryComparator()).take(BEST_LIMIT)
                val nextReserve = (reserve.filterNot { it.id == selected.id } + listOfNotNull(displaced))
                    .distinctBy { it.id }.sortedWith(entryComparator()).take(BEST_LIMIT)

                runCatching {
                    val groupId = ensureExplorerGroup(repository, explorer)
                    val sync = syncBestServers(repository, groupId, nextBest)
                    explorer.publish(
                        snapshot.copy(
                            groupId = groupId,
                            managedProfileIds = sync.managedProfileIds,
                            healthyPool = sync.entries + nextReserve,
                            lastError = null,
                        ),
                    )
                }.onFailure { error ->
                    explorer.updateRuntime { it.copy(lastError = error.message?.take(240) ?: "جایگزینی کانفیگ انجام نشد") }
                }
            }
        }
    }

    private suspend fun runCycle(repository: ConfigRepository) = cycleMutex.withLock {
        val explorer = repository.configExplorer
        if (!explorer.state.value.enabled) return@withLock

        explorer.updateRuntime {
            it.copy(
                isRunning = true,
                phase = "collecting",
                progressDone = 0,
                progressTotal = 0,
                candidateCount = 0,
                liveEntries = emptyList(),
                parsedCount = 0,
                testedCount = 0,
                lastError = null,
            )
        }

        try {
            val fetched = coroutineScope {
                sources.map { source -> async(Dispatchers.IO) { fetchSource(source) } }.awaitAll()
            }
            val statuses = fetched.map { it.status }
            val discovered = linkedMapOf<String, Candidate>()

            explorer.state.value.healthyPool.forEach { old ->
                val source = sources.firstOrNull { it.id == old.sourceId }
                    ?: ConfigExplorerSource(old.sourceId, old.sourceName, "", null, "ذخیره قبلی کاوشگر")
                val key = canonicalKey(old.link)
                if (key.isNotBlank() && !discovered.containsKey(key)) discovered[key] = Candidate(source, old.link)
            }

            fetched.forEach { result ->
                result.links.forEach { link ->
                    if (discovered.size < CANDIDATE_LIMIT) {
                        val key = canonicalKey(link)
                        if (key.isNotBlank() && !discovered.containsKey(key)) {
                            discovered[key] = Candidate(result.source, link)
                        }
                    }
                }
            }

            val candidates = discovered.values.take(CANDIDATE_LIMIT)
            explorer.updateRuntime {
                it.copy(
                    phase = "parsing",
                    candidateCount = candidates.size,
                    sourceStatuses = statuses,
                    progressDone = 0,
                    progressTotal = candidates.size,
                )
            }

            val parsed = candidates.mapIndexedNotNull { index, candidate ->
                val result = runCatching {
                    val profile = VpnProfile.fromAnyLink(
                        link = candidate.link,
                        id = newId(),
                        addedAt = currentTimeMillis(),
                    )
                    require(profile.address.isNotBlank() && profile.address != "-")
                    require(profile.port in 1..65_535)
                    require(!profile.isManagedTunnel && !profile.isDnsBasedTunnel)
                    profile.unsealed().toXrayConfigJson(
                        options = PingService.probeOptions(),
                        forSpeedtest = true,
                    )
                    ParsedCandidate(candidate, profile)
                }.getOrNull()
                explorer.updateRuntime { state ->
                    state.copy(phase = "parsing", progressDone = index + 1, progressTotal = candidates.size)
                }
                result
            }

            explorer.updateRuntime {
                it.copy(
                    phase = "testing_first",
                    parsedCount = parsed.size,
                    progressDone = 0,
                    progressTotal = parsed.size,
                )
            }
            if (parsed.isEmpty()) throw IllegalStateException("هیچ کانفیگ سازگار و قابل‌آزمایشی از منابع دریافت نشد")

            val firstResults = measure(parsed.map { it.profile }, FIRST_TEST_URL, explorer, "testing_first")
            explorer.updateRuntime {
                it.copy(phase = "testing_second", testedCount = firstResults.size, progressDone = 0, progressTotal = parsed.size)
            }
            val secondResults = measure(parsed.map { it.profile }, SECOND_TEST_URL, explorer, "testing_second")
            explorer.updateRuntime { it.copy(testedCount = firstResults.size + secondResults.size) }

            val preliminary = parsed.mapNotNull { item ->
                val first = firstResults[item.profile.id]?.takeIf { it > 0 }
                val second = secondResults[item.profile.id]?.takeIf { it > 0 }
                if (first == null && second == null) return@mapNotNull null
                val preferred = when {
                    first != null && second != null -> if (first <= second) FIRST_TEST_URL else SECOND_TEST_URL
                    first != null -> FIRST_TEST_URL
                    else -> SECOND_TEST_URL
                }
                Preliminary(item, first, second, preferred)
            }.sortedWith(
                compareBy<Preliminary> { medianOf(listOfNotNull(it.firstMs, it.secondMs)) }
                    .thenBy {
                        val values = listOfNotNull(it.firstMs, it.secondMs)
                        if (values.size > 1) values.maxOrNull()!! - values.minOrNull()!! else Int.MAX_VALUE
                    },
            )

            val confirmed = mutableListOf<ConfigExplorerEntry>()
            var offset = 0
            var rechecked = 0
            while (offset < preliminary.size && confirmed.size < HEALTHY_LIMIT) {
                val batch = preliminary.subList(offset, minOf(offset + RECHECK_BATCH_SIZE, preliminary.size))
                val firstFavored = batch.filter { it.preferredUrl == FIRST_TEST_URL }
                val secondFavored = batch.filter { it.preferredUrl == SECOND_TEST_URL }
                val thirdFirst = measure(firstFavored.map { it.parsed.profile }, FIRST_TEST_URL, explorer, "rechecking")
                val thirdSecond = measure(secondFavored.map { it.parsed.profile }, SECOND_TEST_URL, explorer, "rechecking")
                rechecked += batch.size

                batch.forEach { item ->
                    val profile = item.parsed.profile
                    val third = if (item.preferredUrl == FIRST_TEST_URL) thirdFirst[profile.id] else thirdSecond[profile.id]
                    val samples = listOfNotNull(item.firstMs, item.secondMs, third?.takeIf { it > 0 }).sorted()
                    if (samples.size >= 2) {
                        confirmed += ConfigExplorerEntry(
                            id = item.parsed.candidate.source.id + "-" + canonicalKey(item.parsed.candidate.link).hashCode(),
                            name = profile.name,
                            protocol = profile.protocol,
                            address = profile.address,
                            port = profile.port,
                            sourceId = item.parsed.candidate.source.id,
                            sourceName = item.parsed.candidate.source.name,
                            link = item.parsed.candidate.link,
                            samplesMs = samples,
                            medianMs = medianOf(samples),
                            meanMs = samples.average().toInt(),
                            jitterMs = (samples.maxOrNull() ?: 0) - (samples.minOrNull() ?: 0),
                            lastCheckedAt = currentTimeMillis(),
                        ).also { entry ->
                            explorer.updateRuntime { state ->
                                state.copy(liveEntries = (state.liveEntries.filterNot { it.id == entry.id } + entry)
                                    .sortedWith(entryComparator()).take(HEALTHY_LIMIT))
                            }
                            scope.launch {
                                val ip = runCatching { resolveHostAddress(profile.address) }.getOrNull()
                                val country = GeoLookup.countryOf(profile.address)
                                explorer.updateRuntime { state ->
                                    state.copy(liveEntries = state.liveEntries.map { current ->
                                        if (current.id == entry.id) current.copy(ipAddress = ip, countryCode = country) else current
                                    })
                                }
                            }
                        }
                    }
                }

                offset += batch.size
                explorer.updateRuntime {
                    it.copy(
                        phase = "rechecking",
                        testedCount = parsed.size * 2 + rechecked,
                        progressDone = minOf(offset, preliminary.size),
                        progressTotal = preliminary.size,
                    )
                }
            }

            val healthy = confirmed.distinctBy { canonicalKey(it.link) }
                .sortedWith(entryComparator()).take(HEALTHY_LIMIT)
            val finishedAt = currentTimeMillis()

            if (healthy.size < BEST_LIMIT) {
                explorer.publish(
                    explorer.state.value.copy(
                        sourceStatuses = statuses,
                        candidateCount = candidates.size,
                        parsedCount = parsed.size,
                        testedCount = parsed.size * 2 + rechecked,
                        cycleCount = explorer.state.value.cycleCount + 1,
                        lastRunAt = finishedAt,
                        lastError = "فقط " + healthy.size + " کانفیگ حداقل دو تست واقعی موفق داشتند؛ فهرست قبلی برای جلوگیری از جایگزینی ناقص حفظ شد",
                        isRunning = false,
                        phase = "idle",
                        progressDone = rechecked,
                        progressTotal = preliminary.size,
                    ),
                )
                return@withLock
            }

            if (!explorer.state.value.enabled) {
                explorer.updateRuntime { it.copy(isRunning = false, phase = "idle") }
                return@withLock
            }

            explorer.updateRuntime { it.copy(phase = "publishing") }
            val groupId = ensureExplorerGroup(repository, explorer)
            val sync = syncBestServers(repository, groupId, healthy.take(BEST_LIMIT))
            explorer.publish(
                explorer.state.value.copy(
                    groupId = groupId,
                    managedProfileIds = sync.managedProfileIds,
                    healthyPool = sync.entries + healthy.drop(BEST_LIMIT),
                    liveEntries = sync.entries + healthy.drop(BEST_LIMIT),
                    sourceStatuses = statuses,
                    candidateCount = candidates.size,
                    parsedCount = parsed.size,
                    testedCount = parsed.size * 2 + rechecked,
                    cycleCount = explorer.state.value.cycleCount + 1,
                    lastRunAt = finishedAt,
                    lastSuccessAt = finishedAt,
                    lastError = if (healthy.size < HEALTHY_LIMIT) {
                        "در این چرخه " + healthy.size + " کانفیگ سالم تأیید شد؛ تعداد ذخیره کمتر از ۱۰۰ است"
                    } else null,
                    isRunning = false,
                    phase = "idle",
                    progressDone = parsed.size * 2 + rechecked,
                    progressTotal = parsed.size * 2 + rechecked,
                ),
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val finishedAt = currentTimeMillis()
            explorer.publish(
                explorer.state.value.copy(
                    cycleCount = explorer.state.value.cycleCount + 1,
                    lastRunAt = finishedAt,
                    lastError = error.message?.take(240) ?: "خطای ناشناخته در چرخه کاوشگر",
                    isRunning = false,
                    phase = "error",
                ),
            )
        }
    }

    private suspend fun fetchSource(source: ConfigExplorerSource): SourceFetch {
        val feed = source.feedUrl
        if (feed == null) {
            return SourceFetch(
                source,
                emptyList(),
                ConfigExplorerSourceStatus(
                    sourceId = source.id,
                    name = source.name,
                    repositoryUrl = source.repositoryUrl,
                    feedEnabled = false,
                    ok = false,
                    checkedAt = currentTimeMillis(),
                    message = source.description,
                ),
            )
        }

        return runCatching {
            withContext(Dispatchers.IO) {
                httpGetResponse(
                    url = feed,
                    userAgent = "ZX-Config-Explorer/1.0",
                    connectTimeoutMs = 8_000,
                    readTimeoutMs = 15_000,
                )
            }
        }.fold(
            onSuccess = { response ->
                if (response.code !in 200..299) {
                    SourceFetch(
                        source, emptyList(),
                        ConfigExplorerSourceStatus(
                            sourceId = source.id,
                            name = source.name,
                            repositoryUrl = source.repositoryUrl,
                            feedEnabled = true,
                            ok = false,
                            checkedAt = currentTimeMillis(),
                            message = "HTTP " + response.code,
                        ),
                    )
                } else {
                    val links = SubscriptionParser.extractLinks(response.body).distinct()
                    SourceFetch(
                        source, links,
                        ConfigExplorerSourceStatus(
                            sourceId = source.id,
                            name = source.name,
                            repositoryUrl = source.repositoryUrl,
                            feedEnabled = true,
                            ok = true,
                            fetchedCount = links.size,
                            checkedAt = currentTimeMillis(),
                            message = if (links.isEmpty()) "فید دریافت شد اما لینک سازگار پیدا نشد" else "دریافت موفق",
                        ),
                    )
                }
            },
            onFailure = { error ->
                SourceFetch(
                    source, emptyList(),
                    ConfigExplorerSourceStatus(
                        sourceId = source.id,
                        name = source.name,
                        repositoryUrl = source.repositoryUrl,
                        feedEnabled = true,
                        ok = false,
                        checkedAt = currentTimeMillis(),
                        message = error.message?.take(120) ?: "خطا در دریافت فید",
                    ),
                )
            },
        )
    }

    private suspend fun measure(
        profiles: List<VpnProfile>,
        url: String,
        explorer: ConfigExplorerRepository,
        phase: String,
    ): Map<String, Int> {
        if (profiles.isEmpty()) return emptyMap()
        val results = MutableStateFlow<Map<String, Int>>(emptyMap())
        PingService.measureAll(
            profiles = profiles,
            useRealDelay = true,
            concurrency = TEST_CONCURRENCY,
            delayUrl = url,
            detectCountry = false,
            onProgress = { done, total ->
                explorer.updateRuntime { it.copy(phase = phase, progressDone = done, progressTotal = total) }
            },
            onResult = { id, ms, _ ->
                results.update { current -> current + (id to (ms ?: PingService.FAILED_PING)) }
            },
        )
        return results.value
    }

    private fun ensureExplorerGroup(
        repository: ConfigRepository,
        explorer: ConfigExplorerRepository,
    ): String {
        val existingId = explorer.state.value.groupId?.takeIf { id ->
            repository.subscriptions.value.any { it.id == id }
        } ?: repository.subscriptions.value.firstOrNull { it.name == GROUP_NAME && it.url.isBlank() }?.id
        val groupId = existingId ?: repository.addGroup(GROUP_NAME).id
        explorer.setGroupId(groupId)
        return groupId
    }

    private fun syncBestServers(
        repository: ConfigRepository,
        groupId: String,
        best: List<ConfigExplorerEntry>,
    ): SyncResult {
        val activeBefore = repository.activeId.value
        val existing = repository.profiles.value.filter { it.subscriptionId == groupId }
        val existingByKey = existing.mapNotNull { profile ->
            profile.rawPayload()?.let { canonicalKey(it) to profile }
        }.toMap()
        val newlyCreated = mutableSetOf<String>()

        try {
            val synced = best.map { entry ->
                val key = canonicalKey(entry.link)
                val old = existingByKey[key]
                val profile = if (old != null) old else {
                    val beforeIds = repository.profiles.value.map { it.id }.toSet()
                    repository.importText(entry.link, groupId).getOrThrow()
                    val found = repository.profiles.value.firstOrNull {
                        it.id !in beforeIds && it.subscriptionId == groupId &&
                            it.rawPayload()?.let(::canonicalKey) == key
                    } ?: repository.profiles.value.firstOrNull {
                        it.subscriptionId == groupId && it.rawPayload()?.let(::canonicalKey) == key
                    } ?: throw IllegalStateException("کانفیگ تأییدشده به فهرست سرورها اضافه نشد")
                    if (found.id !in beforeIds) newlyCreated += found.id
                    found
                }

                val rebuilt = VpnProfile.fromAnyLink(
                    link = entry.link,
                    id = profile.id,
                    addedAt = profile.addedAt,
                    subscriptionId = groupId,
                    name = profile.name,
                )
                repository.update(
                    rebuilt.copy(
                        lastPingMs = entry.medianMs,
                        countryCode = profile.countryCode,
                        bytesDown = profile.bytesDown,
                        bytesUp = profile.bytesUp,
                        note = profile.note,
                    ),
                )
                entry.copy(profileId = profile.id)
            }

            val desiredIds = synced.mapNotNull { it.profileId }.toSet()
            val stale = existing.filterNot { it.id in desiredIds }
            val activeStale = stale.firstOrNull { it.id == activeBefore }
            if (activeStale != null) repository.update(activeStale.copy(subscriptionId = ""))
            repository.removeAll(stale.map { it.id }.filterNot { it == activeBefore })
            if (repository.activeId.value != activeBefore) repository.setActive(activeBefore)
            return SyncResult(synced, desiredIds.toList())
        } catch (error: Throwable) {
            if (newlyCreated.isNotEmpty()) repository.removeAll(newlyCreated)
            if (repository.activeId.value != activeBefore) repository.setActive(activeBefore)
            throw error
        }
    }

    private fun canonicalKey(link: String): String {
        val clean = link.trim().substringBefore('#')
        if (clean.isBlank()) return ""
        val scheme = clean.substringBefore("://", "")
        return if (scheme.isBlank()) clean else scheme.lowercase() + "://" + clean.substringAfter("://")
    }

    private fun medianOf(values: List<Int>): Int {
        val sorted = values.sorted()
        if (sorted.isEmpty()) return PingService.FAILED_PING
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else {
            ((sorted[middle - 1].toLong() + sorted[middle].toLong()) / 2L).toInt()
        }
    }

    private fun entryComparator(): Comparator<ConfigExplorerEntry> =
        compareBy<ConfigExplorerEntry> { it.medianMs }
            .thenBy { it.jitterMs }
            .thenBy { it.meanMs }
}
